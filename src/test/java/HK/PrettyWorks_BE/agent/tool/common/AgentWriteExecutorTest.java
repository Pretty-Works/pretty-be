package HK.PrettyWorks_BE.agent.tool.common;

import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.json.ParamsCanonicalizer;
import HK.PrettyWorks_BE.agent.suggestion.application.AgentSuggestionCache;
import HK.PrettyWorks_BE.agent.tool.security.InternalAgentAttributes;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentWriteExecutorTest {
    private static final Long RUN_ID = 10L;
    private static final Long APPROVAL_ID = 99L;
    private static final String TOKEN_HASH = "token-hash";
    private static final int TEST_BODY_CACHE_LIMIT = 8 * 1024;
    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParamsCanonicalizer canonicalizer = new ParamsCanonicalizer(objectMapper);
    private final AgentInteractionRepository interactionRepository = mock(AgentInteractionRepository.class);
    private final ApprovalTokenService tokenService = mock(ApprovalTokenService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AgentSuggestionCache suggestionCache = mock(AgentSuggestionCache.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUpTransactionManager() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void replaysTheCommittedResultWithoutExecutingTheServiceAgain() throws IOException {
        String body = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AtomicInteger executions = new AtomicInteger();
        AgentWriteExecutor executor = executor(body);

        var first = executor.execute("task.create", Map.of("projectId", 7), Map.class, key -> {
            assertThat(key).isEqualTo("agent:99");
            executions.incrementAndGet();
            return Map.of("taskId", 123);
        });
        var replay = executor.execute("task.create", Map.of("projectId", 7), Map.class, key -> {
            executions.incrementAndGet();
            return Map.of("taskId", 999);
        });

        assertThat(first.get("taskId")).isEqualTo(123);
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
    }

    // 추천 칩의 재료가 방금 바뀌었다. TTL 이 지나기를 기다리면 "할 일을 끝냈는데 아직도
    // 밀렸다고 한다"가 그 시간만큼 남는다. 재실행(replay)은 이미 지난 실행이라 다시 버리지 않는다.
    @Test
    void dropsTheSuggestionCacheOncePerCommittedWrite() throws IOException {
        String body = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body);

        executor.execute("task.create", Map.of("projectId", 7), Map.class,
                key -> Map.of("taskId", 123));
        executor.execute("task.create", Map.of("projectId", 7), Map.class,
                key -> Map.of("taskId", 999));

        verify(suggestionCache, times(1)).evict(USER_ID);
    }

    @Test
    void rejectsAChangedToolPathOrRawBodyWithoutRevokingTheToken() throws IOException {
        String approvedBody = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", approvedBody);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(approvedBody);

        assertThatThrownBy(() -> executor.execute(
                "task.create", Map.of("projectId", 8), Map.class, () -> Map.of()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AgentErrorCode.PARAMS_MISMATCH));
        assertThatThrownBy(() -> executor.execute(
                "expense.create", Map.of("projectId", 7), Map.class, () -> Map.of()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AgentErrorCode.PARAMS_MISMATCH));
        AgentWriteExecutor changedBody = executor("{\"content\":\"B\",\"projectId\":7}");
        assertThatThrownBy(() -> changedBody.execute(
                "task.create", Map.of("projectId", 7), Map.class, () -> Map.of()))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AgentErrorCode.PARAMS_MISMATCH));

        verifyNoInteractions(tokenService);
    }

    @Test
    void revokesOnlyAfterABusiness4xx() throws IOException {
        String body = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body);

        assertThatThrownBy(() -> executor.execute(
                "task.create", Map.of("projectId", 7), Map.class,
                () -> { throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR); }))
                .isInstanceOf(BaseException.class);

        verify(tokenService).revokeInNewTransaction(APPROVAL_ID);
    }

    @Test
    void keepsTheTokenAfterABusiness5xx() throws IOException {
        String body = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body);

        assertThatThrownBy(() -> executor.execute(
                "task.create", Map.of("projectId", 7), Map.class,
                () -> { throw BaseException.type(GlobalErrorCode.INTERNAL_SERVER_ERROR); }))
                .isInstanceOf(BaseException.class);

        verifyNoInteractions(tokenService);
    }

    @Test
    void validatesTheBusinessBodyInsideTheExecutorAndRevokesItsToken() throws IOException {
        String body = "{\"name\":\"\"}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body);

        assertThatThrownBy(() -> executor.executeValidated(
                "task.create", Map.of(), InvalidBusinessRequest.class, Map.class,
                (request, key) -> Map.of("unexpected", true)))
                .isInstanceOf(BaseException.class);

        verify(tokenService).revokeInNewTransaction(APPROVAL_ID);
    }

    @Test
    void preservesAndValidatesBatchElementTypes() throws IOException {
        String body = "[{\"name\":\"first\"},{\"name\":\"\"}]";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body);

        assertThatThrownBy(() -> executor.executeValidated(
                "task.create", Map.of(),
                new TypeReference<List<InvalidBusinessRequest>>() {},
                new TypeReference<List<Map<String, Object>>>() {},
                (requests, key) -> List.of()))
                .isInstanceOf(BaseException.class);

        verify(tokenService).revokeInNewTransaction(APPROVAL_ID);
    }

    @Test
    void readsAndCachesTheRawBodyWhenTheControllerDidNotBindIt() throws IOException {
        String body = "{\"content\":\"A\",\"projectId\":7}";
        AgentInteractionEntity interaction = approvedInteraction("task.create", body);
        when(interactionRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(interaction));
        AgentWriteExecutor executor = executor(body, false);

        Map<?, ?> result = executor.execute(
                "task.create", Map.of("projectId", 7), Map.class,
                () -> Map.of("taskId", 123));

        assertThat(result.get("taskId")).isEqualTo(123);
    }

    private AgentWriteExecutor executor(String body) throws IOException {
        return executor(body, true);
    }

    private AgentWriteExecutor executor(String body, boolean preReadBody) throws IOException {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest();
        rawRequest.setContent(body.getBytes(StandardCharsets.UTF_8));
        // Spring Framework 7부터 캐시 상한이 필수 인자다. 이 테스트가 보는 건 해시 대조라
        // 상한 자체는 의미가 없고, 본문(수십 바이트)이 잘리지 않을 만큼만 있으면 된다.
        ContentCachingRequestWrapper request =
                new ContentCachingRequestWrapper(rawRequest, TEST_BODY_CACHE_LIMIT);
        if (preReadBody) {
            request.getInputStream().readAllBytes();
        }
        request.setAttribute(InternalAgentAttributes.CACHED_REQUEST, request);
        request.setAttribute(InternalAgentAttributes.INTERNAL_RUN_ID, RUN_ID);
        request.setAttribute(InternalAgentAttributes.USER_ID, USER_ID);
        request.setAttribute(InternalAgentAttributes.TOKEN_INSPECTION,
                new ApprovalTokenService.TokenInspection(APPROVAL_ID, TOKEN_HASH));
        return new AgentWriteExecutor(request, interactionRepository, tokenService,
                canonicalizer, objectMapper, validator, transactionManager, suggestionCache);
    }

    private AgentInteractionEntity approvedInteraction(String tool, String body) {
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(RUN_ID)
                .kind(AgentInteractionKind.APPROVAL)
                .label("할 일 생성")
                .toolCallId("call-1")
                .tool(tool)
                .access(AgentAccessType.WRITE)
                .paramsCanonical(body)
                .paramsHash(canonicalizer.hashRaw(body.getBytes(StandardCharsets.UTF_8)))
                .autoApproved(true)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .resolvedAt(LocalDateTime.now())
                .build();
        interaction.issueToken(TOKEN_HASH, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(interaction, "id", APPROVAL_ID);
        return interaction;
    }

    private record InvalidBusinessRequest(@NotBlank String name) {
    }
}
