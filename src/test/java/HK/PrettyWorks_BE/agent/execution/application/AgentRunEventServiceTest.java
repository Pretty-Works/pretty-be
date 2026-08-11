package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.application.AgentMessageStepService;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentEventEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentEventSignalPublisher;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.interaction.application.AgentInteractionService;
import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRunEventServiceTest {
    private static final Long RUN_ID = 10L;
    private static final Long CONVERSATION_ID = 20L;
    private static final Long INTERACTION_ID = 30L;

    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentMessageStepService messageStepService = mock(AgentMessageStepService.class);
    private final AgentEventRepository eventRepository = mock(AgentEventRepository.class);
    private final AgentInteractionService interactionService = mock(AgentInteractionService.class);
    private final AgentInteractionRepository interactionRepository =
            mock(AgentInteractionRepository.class);
    private final AgentStreamService streamService = mock(AgentStreamService.class);
    private final ApprovalTokenService tokenService = mock(ApprovalTokenService.class);
    private final AgentEventSignalPublisher eventSignalPublisher =
            mock(AgentEventSignalPublisher.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentRunEntity run;
    private AgentRunEventService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        run = runningRun();
        when(runRepository.findByIdForUpdate(RUN_ID)).thenReturn(Optional.of(run));
        when(messageRepository.save(any(AgentMessageEntity.class))).thenAnswer(invocation -> {
            AgentMessageEntity message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 200L);
            return message;
        });
        when(streamService.appendInCurrentTransaction(eq(RUN_ID), anyString(), anyString()))
                .thenAnswer(invocation -> persistedEvent(
                        invocation.getArgument(1), invocation.getArgument(2)));

        AgentRunStateMachine stateMachine =
                new AgentRunStateMachine(runRepository, transactionManager);
        service = new AgentRunEventService(runRepository, conversationRepository,
                messageRepository, messageStepService, eventRepository,
                interactionService, interactionRepository, stateMachine,
                streamService, tokenService, eventSignalPublisher, objectMapper, transactionManager);
    }

    @Test
    void manualApprovalAddsServerOwnedAlternativeAndClosesOnlyAfterCommit() {
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(false)));
        AgentInteractionEntity interaction = approvalInteraction(false);
        when(interactionService.createApproval(eq(RUN_ID), eq("call-1"), anyString(),
                eq("task.create"), any(), isNull(), eq(false))).thenReturn(interaction);

        AgentRunEventService.HandlingResult result = service.handle(RUN_ID, approvalEvent());

        assertThat(result.disposition())
                .isEqualTo(AgentRunEventService.Disposition.WAITING_APPROVAL);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.WAITING_APPROVAL);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamService).appendInCurrentTransaction(
                eq(RUN_ID), eq("approval_request"), payload.capture());
        assertThat(objectMapper.readTree(payload.getValue()).at("/approvalId").asLong())
                .isEqualTo(INTERACTION_ID);
        assertThat(objectMapper.readTree(payload.getValue()).at("/previewText").asText())
                .isEqualTo("서버 미리보기");
        JsonNode delivered = objectMapper.readTree(payload.getValue());
        assertThat(delivered.at("/alternatives/0/id").asText()).isEqualTo("FILL_FORM");
        assertThat(delivered.at("/alternatives/1/id").asText()).isEqualTo("ALWAYS");
        assertThat(delivered.path("alternatives")).hasSize(2);
        verifyNoInteractions(tokenService);

        InOrder order = inOrder(transactionManager, streamService);
        order.verify(transactionManager).commit(any());
        order.verify(streamService).deliver(any(AgentEventEntity.class));
        order.verify(streamService).completeSegment(RUN_ID);
    }

    @Test
    void autoApprovalReturnsRedactedResumeCredentialsAndKeepsSegmentOpen() {
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(true)));
        AgentInteractionEntity interaction = approvalInteraction(true);
        when(interactionService.createApproval(eq(RUN_ID), eq("call-1"), anyString(),
                eq("task.create"), any(), isNull(), eq(true))).thenReturn(interaction);
        when(tokenService.issue(INTERACTION_ID)).thenReturn(
                new ApprovalTokenService.IssuedToken("secret-token", LocalDateTime.now()));

        AgentRunEventService.HandlingResult result = service.handle(RUN_ID, approvalEvent());

        assertThat(result.disposition())
                .isEqualTo(AgentRunEventService.Disposition.AUTO_APPROVAL);
        assertThat(result.autoApproval().approvalToken()).isEqualTo("secret-token");
        assertThat(result.autoApproval().paramsCanonical()).isEqualTo("{\"taskId\":7}");
        assertThat(result.toString()).doesNotContain("secret-token", "taskId");
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        verify(streamService).deliver(any(AgentEventEntity.class));
        verify(streamService, never()).completeSegment(anyLong());
        verify(streamService, never()).completeRun(anyLong());
    }

    @Test
    void cancellationRevokesUnexecutedApprovalAndExpiresPendingQuestionAtomically() {
        AgentConversationEntity conversation = conversation(false);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        AgentInteractionEntity approved = approvalInteraction(true);
        approved.issueToken("token-hash", LocalDateTime.now().plusMinutes(10));
        AgentInteractionEntity question = pendingQuestionInteraction();
        when(interactionRepository.findByRunIdOrderById(RUN_ID))
                .thenReturn(List.of(approved, question));

        AgentRunEventService.HandlingResult result = service.cancelActiveRun(RUN_ID);

        assertThat(result.disposition()).isEqualTo(AgentRunEventService.Disposition.FAILED);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo(AgentErrorCode.RUN_CANCELED.getErrorCode());
        assertThat(approved.getTokenRevokedAt()).isNotNull();
        assertThat(question.getStatus()).isEqualTo(
                HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus.EXPIRED);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(streamService).appendInCurrentTransaction(eq(RUN_ID), eq("error"), payload.capture());
        assertThat(objectMapper.readTree(payload.getValue()).path("code").asText())
                .isEqualTo("AGENT_027");
        verify(streamService).completeRun(RUN_ID);
    }

    @Test
    void doneCommitsMessageStateAndReplayEventBeforeLiveDelivery() {
        AgentConversationEntity conversation = conversation(false);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "done");
        payload.put("answer", "완료했습니다");
        ObjectNode actionPayload = payload.putObject("action");
        actionPayload.put("type", "NAVIGATE");
        actionPayload.put("label", "보러 가기");
        AgentServerEvent.Action action = new AgentServerEvent.Action(
                "NAVIGATE", "보러 가기", "/tasks", null, null);

        AgentRunEventService.HandlingResult result = service.handle(RUN_ID,
                new DecodedAgentServerEvent(
                        new AgentServerEvent.Done("완료했습니다", action), payload));

        assertThat(result.disposition()).isEqualTo(AgentRunEventService.Disposition.COMPLETED);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        ArgumentCaptor<AgentMessageEntity> message =
                ArgumentCaptor.forClass(AgentMessageEntity.class);
        verify(messageRepository).save(message.capture());
        assertThat(message.getValue().getRole()).isEqualTo(AgentRole.AGENT);
        assertThat(message.getValue().getSuccess()).isTrue();
        assertThat(message.getValue().getActionType()).isEqualTo("NAVIGATE");
        verify(messageStepService).copyFromRun(200L, RUN_ID);
        assertThat(conversation.getLastMessageAt()).isAfter(LocalDateTime.now().minusSeconds(2));

        InOrder order = inOrder(transactionManager, streamService);
        order.verify(transactionManager).commit(any());
        order.verify(streamService).deliver(any(AgentEventEntity.class));
        order.verify(streamService).completeRun(RUN_ID);
    }

    @Test
    void questionLimitFailsAtomicallyWithoutCreatingAnInteraction() {
        ReflectionTestUtils.setField(run, "questionCount", AgentRunStateMachine.MAX_QUESTIONS);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(false)));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "question");
        payload.put("label", "확인");
        payload.put("text", "계속할까요?");
        AgentServerEvent.Question question = new AgentServerEvent.Question(
                "확인", "계속할까요?", List.of(), false, true);

        AgentRunEventService.HandlingResult result = service.handle(RUN_ID,
                new DecodedAgentServerEvent(question, payload));

        assertThat(result.disposition()).isEqualTo(AgentRunEventService.Disposition.FAILED);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        verifyNoInteractions(interactionService);
        verify(messageRepository).save(any(AgentMessageEntity.class));
        verify(streamService).completeRun(RUN_ID);
    }

    @Test
    void twentyFirstAuthenticatedToolAttemptFailsTheRunAndClosesEverySegment() {
        ReflectionTestUtils.setField(run, "toolCallCount", AgentRunStateMachine.MAX_TOOL_CALLS);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(false)));

        assertThatThrownBy(() -> service.registerToolCall(RUN_ID))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(AgentErrorCode.TOOL_CALL_LIMIT_EXCEEDED));

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        verify(messageRepository).save(any(AgentMessageEntity.class));
        verify(streamService).deliver(any(AgentEventEntity.class));
        verify(eventSignalPublisher).publish(any(AgentEventEntity.class));
        verify(streamService).completeRun(RUN_ID);
    }

    @Test
    void stepLimitFailsBeforePersistingTheNextStep() {
        when(eventRepository.countByRunIdAndEventType(RUN_ID, "step"))
                .thenReturn(100L);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(false)));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "step");
        payload.put("text", "one too many");

        AgentRunEventService.HandlingResult result = service.handle(RUN_ID,
                new DecodedAgentServerEvent(
                        new AgentServerEvent.Step("one too many"), payload));

        assertThat(result.disposition()).isEqualTo(AgentRunEventService.Disposition.FAILED);
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        verify(streamService).appendInCurrentTransaction(
                eq(RUN_ID), eq("error"), anyString());
        verify(streamService, never()).appendInCurrentTransaction(
                eq(RUN_ID), eq("step"), anyString());
        verify(streamService).completeRun(RUN_ID);
    }

    private DecodedAgentServerEvent approvalEvent() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("taskId", 7);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "approval_request");
        payload.put("toolCallId", "call-1");
        payload.put("tool", "task.create");
        payload.put("access", "WRITE");
        payload.put("summary", "업무 생성");
        payload.set("params", params);
        ArrayNode alternatives = payload.putArray("alternatives");
        alternatives.addObject().put("id", "ALWAYS").put("label", "조작된 선택지");
        alternatives.addObject().put("id", "FILL_FORM").put("label", "수정하기");
        AgentServerEvent.ApprovalRequest approval = new AgentServerEvent.ApprovalRequest(
                "call-1", "task.create", AgentAccessType.WRITE, "업무 생성",
                "LLM 미리보기", params, List.of());
        return new DecodedAgentServerEvent(approval, payload);
    }

    private AgentRunEntity runningRun() {
        AgentRunEntity entity = AgentRunEntity.builder()
                .runId("public-run")
                .conversationId(CONVERSATION_ID)
                .userId(1L)
                .goal("업무를 만들어줘")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(entity, "id", RUN_ID);
        return entity;
    }

    private AgentConversationEntity conversation(boolean autoApprove) {
        return AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now().minusMinutes(1))
                .autoApprove(autoApprove)
                .build();
    }

    private AgentInteractionEntity approvalInteraction(boolean autoApproved) {
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(RUN_ID)
                .kind(AgentInteractionKind.APPROVAL)
                .label("업무 생성")
                .toolCallId("call-1")
                .tool("task.create")
                .access(AgentAccessType.WRITE)
                .previewText("서버 미리보기")
                .paramsCanonical("{\"taskId\":7}")
                .paramsHash("hash")
                .autoApproved(autoApproved)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .resolvedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(interaction, "id", INTERACTION_ID);
        return interaction;
    }

    private AgentInteractionEntity pendingQuestionInteraction() {
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(RUN_ID)
                .kind(AgentInteractionKind.QUESTION)
                .label("추가 질문")
                .payloadJson("{\"options\":[]}")
                .questionText("내용을 알려주세요")
                .autoApproved(false)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        ReflectionTestUtils.setField(interaction, "id", 31L);
        return interaction;
    }

    private AgentEventEntity persistedEvent(String eventType, String payload) {
        AgentEventEntity event = new AgentEventEntity(RUN_ID, 1L, eventType, payload);
        ReflectionTestUtils.setField(event, "id", 100L);
        return event;
    }
}
