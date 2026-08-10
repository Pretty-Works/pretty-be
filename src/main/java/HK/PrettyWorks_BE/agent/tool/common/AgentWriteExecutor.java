package HK.PrettyWorks_BE.agent.tool.common;

import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.json.ParamsCanonicalizer;
import HK.PrettyWorks_BE.agent.tool.security.InternalAgentAttributes;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Service
public class AgentWriteExecutor {
    private final HttpServletRequest request;
    private final AgentInteractionRepository interactionRepository;
    private final ApprovalTokenService approvalTokenService;
    private final ParamsCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;

    public AgentWriteExecutor(HttpServletRequest request,
                              AgentInteractionRepository interactionRepository,
                              ApprovalTokenService approvalTokenService,
                              ParamsCanonicalizer canonicalizer,
                              ObjectMapper objectMapper,
                              Validator validator,
                              PlatformTransactionManager transactionManager) {
        this.request = request;
        this.interactionRepository = interactionRepository;
        this.approvalTokenService = approvalTokenService;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executes a fixed controller-owned tool. The function receives the legacy-service
     * idempotency key in the form {@code agent:{approvalId}}.
     */
    <T> T execute(String tool, Map<String, ?> actualPathTargets,
                         Class<T> resultType, Function<String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType), action);
    }

    <T> T execute(String tool, Map<String, ?> actualPathTargets,
                         Class<T> resultType, Supplier<T> action) {
        return execute(tool, actualPathTargets, resultType, ignored -> action.get());
    }

    // 배치 도구(task.create 등)는 List<XxxResponse> 를 돌려주는데 Class<T> 로는 제네릭 타입을
    // 표현할 수 없다. 그래서 TypeReference 오버로드를 함께 둔다.
    <T> T execute(String tool, Map<String, ?> actualPathTargets,
                         TypeReference<T> resultType, Function<String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType), action);
    }

    <T> T execute(String tool, Map<String, ?> actualPathTargets,
                         TypeReference<T> resultType, Supplier<T> action) {
        return execute(tool, actualPathTargets, resultType, ignored -> action.get());
    }

    /**
     * Public WRITE entry point. Controllers must not bind an {@code @Valid} business DTO before
     * this call; conversion and validation happen after approval verification so every business
     * 4xx is owned and revoked by this executor.
     */
    public <B, T> T executeValidated(String tool, Map<String, ?> actualPathTargets,
                                     Class<B> bodyType, Class<T> resultType,
                                     BiFunction<B, String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType),
                idempotencyKey -> action.apply(readAndValidate(bodyType), idempotencyKey));
    }

    public <B, T> T executeValidated(String tool, Map<String, ?> actualPathTargets,
                                     Class<B> bodyType, TypeReference<T> resultType,
                                     BiFunction<B, String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType),
                idempotencyKey -> action.apply(readAndValidate(bodyType), idempotencyKey));
    }

    /** Preserves generic request element types for batch WRITE tools such as task.create. */
    public <B, T> T executeValidated(String tool, Map<String, ?> actualPathTargets,
                                     TypeReference<B> bodyType, Class<T> resultType,
                                     BiFunction<B, String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType),
                idempotencyKey -> action.apply(readAndValidate(bodyType), idempotencyKey));
    }

    public <B, T> T executeValidated(String tool, Map<String, ?> actualPathTargets,
                                     TypeReference<B> bodyType, TypeReference<T> resultType,
                                     BiFunction<B, String, T> action) {
        return execute(tool, actualPathTargets,
                json -> objectMapper.readValue(json, resultType),
                idempotencyKey -> action.apply(readAndValidate(bodyType), idempotencyKey));
    }

    // replayReader 는 저장해 둔 result_json 을 원래 타입으로 되돌리는 방법이다.
    // Class·TypeReference 두 갈래를 여기서 하나로 모은다.
    private <T> T execute(String tool, Map<String, ?> actualPathTargets,
                          Function<String, T> replayReader, Function<String, T> action) {
        ApprovalTokenService.TokenInspection inspection = tokenInspection();
        byte[] rawBody = rawBody();
        boolean[] businessStarted = {false};
        try {
            return transactionTemplate.execute(status -> {
                AgentInteractionEntity interaction = interactionRepository
                        .findByIdForUpdate(inspection.interactionId())
                        .orElseThrow(() -> BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN));
                verifyInteraction(interaction, inspection, tool, rawBody, actualPathTargets);

                // 이미 커밋된 실행이면 업무 로직을 다시 돌리지 않고 저장해 둔 결과를 그대로 돌려준다.
                // 응답만 유실된 재시도에서 리소스가 두 번 생기는 것을 막는다.
                if (interaction.getExecutedAt() != null) {
                    return replayReader.apply(interaction.getResultJson());
                }

                businessStarted[0] = true;
                T result = action.apply("agent:" + interaction.getId());
                interaction.completeExecution(objectMapper.writeValueAsString(result),
                        LocalDateTime.now());
                return result;
            });
        } catch (BaseException exception) {
            // 승인 토큰 폐기는 이 메서드가 단독으로 소유한다. 필터는 HTTP 상태 코드로 업무 의미를
            // 추론하지 않는다 — 그렇게 하면 응답을 이미 내보낸 뒤 폐기가 실패했을 때
            // 클라이언트가 받은 결과와 실제 토큰 상태가 갈린다.
            //
            // 업무 4xx만 폐기한다. 5xx는 일시 장애라 같은 토큰으로 재시도할 수 있어야 하고,
            // AGENT_015 는 승인 내용과 body가 다른 것이라 원래 canonical 로 다시 보내면 된다.
            //
            // 공개 WRITE 진입점은 DTO 변환과 검증도 action 안에서 수행하므로 그 4xx까지 여기서 폐기한다.
            // 컨트롤러가 @RequestBody/@Valid로 먼저 바인딩하지 않게 공개 API를 제한한 이유다.
            if (businessStarted[0]
                    && exception.getCode().getStatus().is4xxClientError()
                    && exception.getCode() != AgentErrorCode.PARAMS_MISMATCH) {
                approvalTokenService.revokeInNewTransaction(inspection.interactionId());
            }
            throw exception;
        }
    }

    private void verifyInteraction(AgentInteractionEntity interaction,
                                   ApprovalTokenService.TokenInspection inspection,
                                   String tool, byte[] rawBody,
                                   Map<String, ?> actualPathTargets) {
        LocalDateTime now = LocalDateTime.now();
        if (!interaction.getRunId().equals(internalRunId())
                || !interaction.isWriteApproval()
                || interaction.getStatus() != AgentInteractionStatus.APPROVED
                || interaction.getTokenHash() == null
                || !interaction.getTokenHash().equals(inspection.tokenHash())
                || interaction.getTokenRevokedAt() != null) {
            throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
        }
        if (interaction.getExecutedAt() == null && interaction.tokenExpired(now)) {
            throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
        }
        if (!tool.equals(interaction.getTool())
                || !canonicalizer.hashRaw(rawBody).equals(interaction.getParamsHash())) {
            throw BaseException.type(AgentErrorCode.PARAMS_MISMATCH);
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody);
        } catch (RuntimeException invalidJson) {
            throw BaseException.type(AgentErrorCode.PARAMS_MISMATCH);
        }
        for (Map.Entry<String, ?> target : actualPathTargets.entrySet()) {
            JsonNode bodyValue = body == null ? null : body.get(target.getKey());
            if (bodyValue == null || !canonicalizer.canonicalize(bodyValue).value().equals(
                    canonicalizer.canonicalize(target.getValue()).value())) {
                throw BaseException.type(AgentErrorCode.PARAMS_MISMATCH);
            }
        }
    }

    private ApprovalTokenService.TokenInspection tokenInspection() {
        Object value = request.getAttribute(InternalAgentAttributes.TOKEN_INSPECTION);
        if (!(value instanceof ApprovalTokenService.TokenInspection inspection)) {
            throw BaseException.type(AgentErrorCode.INVALID_APPROVAL_TOKEN);
        }
        return inspection;
    }

    private <B> B readAndValidate(Class<B> bodyType) {
        final B body;
        try {
            body = objectMapper.readValue(rawBody(), bodyType);
        } catch (RuntimeException invalidBody) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        validateBody(body);
        return body;
    }

    private <B> B readAndValidate(TypeReference<B> bodyType) {
        final B body;
        try {
            body = objectMapper.readValue(rawBody(), bodyType);
        } catch (RuntimeException invalidBody) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        validateBody(body);
        return body;
    }

    private void validateBody(Object body) {
        if (body == null || !validator.validate(body).isEmpty()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        if (body instanceof Iterable<?> elements) {
            for (Object element : elements) {
                if (element == null || !validator.validate(element).isEmpty()) {
                    throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
                }
            }
        }
    }

    private Long internalRunId() {
        Object value = request.getAttribute(InternalAgentAttributes.INTERNAL_RUN_ID);
        if (!(value instanceof Long runId)) {
            throw BaseException.type(AgentErrorCode.RUN_NOT_FOUND);
        }
        return runId;
    }

    private byte[] rawBody() {
        Object value = request.getAttribute(InternalAgentAttributes.CACHED_REQUEST);
        if (!(value instanceof ContentCachingRequestWrapper wrapper)) {
            throw BaseException.type(AgentErrorCode.PARAMS_MISMATCH);
        }

        // ContentCachingRequestWrapper records bytes only while a downstream consumer reads them.
        // Internal WRITE controllers intentionally do not bind @RequestBody before approval
        // verification, so the executor must become that first consumer.
        if (wrapper.getContentAsByteArray().length == 0 && wrapper.getContentLengthLong() > 0) {
            try {
                wrapper.getInputStream().readAllBytes();
            } catch (IOException unreadableBody) {
                throw BaseException.type(AgentErrorCode.PARAMS_MISMATCH);
            }
        }
        return wrapper.getContentAsByteArray();
    }
}
