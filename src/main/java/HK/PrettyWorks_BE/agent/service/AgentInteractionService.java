package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentAccessType;
import HK.PrettyWorks_BE.agent.constant.AgentDecision;
import HK.PrettyWorks_BE.agent.constant.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.constant.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.constant.AgentPayloadLimits;
import HK.PrettyWorks_BE.agent.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentInteractionRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@Service
public class AgentInteractionService {
    private static final String ALWAYS_ID = "ALWAYS";

    // 답을 기다리는 시간. 이 시간을 넘기면 실행도 EXPIRED로 함께 닫힌다.
    // 실행 수명(agent.run.max-duration-minutes)보다 짧아야 한다 — 길게 두면 실행이 먼저 죽어
    // 답할 수 없는 카드만 남는다.
    private final long interactionTtlMinutes;

    private final AgentInteractionRepository interactionRepository;
    private final ParamsCanonicalizer canonicalizer;
    private final ApprovalPreviewRegistry previewRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AgentInteractionService(AgentInteractionRepository interactionRepository,
                                   ParamsCanonicalizer canonicalizer,
                                   ApprovalPreviewRegistry previewRegistry,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${agent.interaction.ttl-minutes}") long interactionTtlMinutes) {
        this.interactionRepository = interactionRepository;
        this.canonicalizer = canonicalizer;
        this.previewRegistry = previewRegistry;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.interactionTtlMinutes = interactionTtlMinutes;
    }

    public AgentInteractionEntity createApproval(Long internalRunId, String toolCallId,
                                                  String label, String tool, Object params,
                                                  boolean autoApproved) {
        return createApproval(internalRunId, toolCallId, label, tool, params, null, autoApproved);
    }

    public AgentInteractionEntity createApproval(Long internalRunId, String toolCallId,
                                                  String label, String tool, Object params,
                                                  Object displayPayload, boolean autoApproved) {
        JsonNode paramsNode = objectMapper.valueToTree(params);
        if (toolCallId == null || toolCallId.isBlank() || tool == null || tool.isBlank()
                || label == null || label.isBlank() || paramsNode == null || paramsNode.isNull()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        ParamsCanonicalizer.CanonicalParams canonical = canonicalizer.canonicalize(paramsNode);
        if (canonical.value().getBytes(StandardCharsets.UTF_8).length
                > AgentPayloadLimits.MAX_PARAMS_CANONICAL_BYTES) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        String payloadJson = displayPayload == null
                ? null
                : objectMapper.writeValueAsString(displayPayload);
        try {
            return transactionTemplate.execute(status -> interactionRepository
                    .findByRunIdAndToolCallId(internalRunId, toolCallId)
                    .map(existing -> requireSameApproval(existing, tool, canonical.hash()))
                    .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    return interactionRepository.save(AgentInteractionEntity.builder()
                            .runId(internalRunId)
                            .kind(AgentInteractionKind.APPROVAL)
                            .label(label)
                            .payloadJson(payloadJson)
                            .toolCallId(toolCallId)
                            .tool(tool)
                            .access(AgentAccessType.WRITE)
                            .previewText(previewRegistry.render(tool, paramsNode))
                            .paramsCanonical(canonical.value())
                            .paramsHash(canonical.hash())
                            .autoApproved(autoApproved)
                            .expiresAt(now.plusMinutes(interactionTtlMinutes))
                            .resolvedAt(now)
                            .build());
                    }));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return interactionRepository.findByRunIdAndToolCallId(internalRunId, toolCallId)
                    .map(existing -> requireSameApproval(existing, tool, canonical.hash()))
                    .orElseThrow(() -> concurrentDuplicate);
        }
    }

    /** 질문 횟수를 상태 머신에서 먼저 등록한 같은 트랜잭션 안에서 호출합니다. */
    public AgentInteractionEntity createQuestionAfterRegistration(
            Long internalRunId, String label, String questionText, Object payload) {
        if (label == null || label.isBlank() || questionText == null || questionText.isBlank()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            String payloadJson = payload == null ? null : objectMapper.writeValueAsString(payload);
            return interactionRepository.save(AgentInteractionEntity.builder()
                    .runId(internalRunId)
                    .kind(AgentInteractionKind.QUESTION)
                    .label(label)
                    .payloadJson(payloadJson)
                    .questionText(questionText)
                    .autoApproved(false)
                    .expiresAt(now.plusMinutes(interactionTtlMinutes))
                    .build());
        });
    }

    public void resolve(Long interactionId, Resolution resolution) {
        ResolutionOutcome outcome = transactionTemplate.execute(status -> {
            AgentInteractionEntity interaction = interactionRepository.findById(interactionId)
                    .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND));
            LocalDateTime now = LocalDateTime.now();
            if (interaction.getStatus() != AgentInteractionStatus.PENDING) {
                return ResolutionOutcome.ALREADY_RESOLVED;
            }
            if (!interaction.getExpiresAt().isAfter(now)) {
                int updated = interactionRepository.resolvePending(
                        interactionId, AgentInteractionStatus.PENDING,
                        AgentInteractionStatus.EXPIRED, null, null, null, null, now);
                return updated == 1
                        ? ResolutionOutcome.EXPIRED : ResolutionOutcome.ALREADY_RESOLVED;
            }
            validateResolution(interaction, resolution);
            int updated = interactionRepository.resolvePending(interactionId,
                    AgentInteractionStatus.PENDING, resolution.status(), resolution.decision(),
                    resolution.alternativeId(), resolution.responseJson(), resolution.reason(), now);
            return updated == 1 ? ResolutionOutcome.RESOLVED : ResolutionOutcome.ALREADY_RESOLVED;
        });

        if (outcome == ResolutionOutcome.EXPIRED) {
            throw BaseException.type(AgentErrorCode.INTERACTION_EXPIRED);
        }
        if (outcome == ResolutionOutcome.ALREADY_RESOLVED) {
            throw BaseException.type(AgentErrorCode.INTERACTION_ALREADY_RESOLVED);
        }
    }

    public record Resolution(AgentInteractionStatus status, AgentDecision decision,
                             String alternativeId, String responseJson, String reason) {}

    private AgentInteractionEntity requireSameApproval(AgentInteractionEntity existing,
                                                       String tool, String paramsHash) {
        if (!tool.equals(existing.getTool()) || !paramsHash.equals(existing.getParamsHash())) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return existing;
    }

    private void validateResolution(AgentInteractionEntity interaction, Resolution resolution) {
        boolean validApproval = resolution != null && resolution.status() != null
                && interaction.getKind() == AgentInteractionKind.APPROVAL
                && resolution.decision() != null
                && resolution.status().name().equals(resolution.decision().name());
        boolean validQuestion = resolution != null && resolution.status() != null
                && interaction.getKind() == AgentInteractionKind.QUESTION
                && resolution.status() == AgentInteractionStatus.ANSWERED
                && resolution.decision() == null;
        if (!validApproval && !validQuestion) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        if (validApproval) {
            boolean alternative = resolution.status() == AgentInteractionStatus.ALTERNATIVE;
            boolean always = resolution.status() == AgentInteractionStatus.APPROVED
                    && ALWAYS_ID.equals(resolution.alternativeId());
            boolean hasAlternativeId = resolution.alternativeId() != null
                    && !resolution.alternativeId().isBlank();
            if (!always && alternative != hasAlternativeId) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
            if ((alternative || always)
                    && !containsAlternative(interaction, resolution.alternativeId())) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
        }
        if (validQuestion && (resolution.responseJson() == null
                || resolution.responseJson().isBlank())) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean containsAlternative(AgentInteractionEntity interaction, String alternativeId) {
        try {
            JsonNode payload = objectMapper.readTree(interaction.getPayloadJson());
            JsonNode alternatives = payload == null ? null : payload.get("alternatives");
            if (alternatives == null || !alternatives.isArray()) {
                return false;
            }
            for (JsonNode alternative : alternatives) {
                JsonNode id = alternative.get("id");
                if (id != null && alternativeId.equals(id.textValue())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException invalidPayload) {
            return false;
        }
    }

    private enum ResolutionOutcome { RESOLVED, EXPIRED, ALREADY_RESOLVED }
}
