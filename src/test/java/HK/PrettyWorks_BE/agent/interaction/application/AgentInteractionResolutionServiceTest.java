package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.execution.application.AgentRunStateMachine;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentInteractionResolutionServiceTest {
    private static final Long RUN_ID = 10L;
    private static final Long CONVERSATION_ID = 20L;
    private static final Long INTERACTION_ID = 30L;

    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentInteractionRepository interactionRepository =
            mock(AgentInteractionRepository.class);
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentInteractionService interactionService = mock(AgentInteractionService.class);
    private final AgentRunStateMachine stateMachine = mock(AgentRunStateMachine.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);

    private AgentInteractionResolutionService service;

    @BeforeEach
    void setUp() {
        service = new AgentInteractionResolutionService(runRepository, interactionRepository,
                conversationRepository, interactionService, stateMachine, objectMapper,
                currentUserService);
    }

    @Test
    void alwaysIsStoredForAuditButFastApiReceivesOrdinaryApproval() {
        AgentRunEntity run = run(AgentRunStatus.WAITING_APPROVAL);
        AgentInteractionEntity interaction = approvalInteraction();
        AgentConversationEntity conversation = conversation();
        prepareLocks(run, interaction);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        when(stateMachine.transitionLocked(eq(run),
                eq(EnumSet.of(AgentRunStatus.WAITING_APPROVAL)), eq(AgentRunStatus.RUNNING),
                isNull())).thenReturn(true);

        AgentInteractionResolutionService.PreparedApproval prepared = service.resolveApproval(
                1L, INTERACTION_ID,
                new AgentApprovalRequest(AgentDecision.ALTERNATIVE, "ALWAYS", null));

        ArgumentCaptor<AgentInteractionService.Resolution> resolution =
                ArgumentCaptor.forClass(AgentInteractionService.Resolution.class);
        verify(interactionService).resolve(eq(INTERACTION_ID), resolution.capture());
        assertThat(resolution.getValue().status()).isEqualTo(AgentInteractionStatus.APPROVED);
        assertThat(resolution.getValue().decision()).isEqualTo(AgentDecision.APPROVED);
        assertThat(resolution.getValue().alternativeId()).isEqualTo("ALWAYS");
        assertThat(prepared.decision()).isEqualTo(AgentDecision.APPROVED);
        assertThat(prepared.alternativeId()).isNull();
        assertThat(prepared.tokenRequired()).isTrue();
        assertThat(prepared.paramsCanonical()).isEqualTo("{\"taskId\":7}");
        assertThat(conversation.isAutoApprove()).isTrue();
    }

    @Test
    void questionResponseValidatesOptionsAndPreservesFreeText() {
        AgentRunEntity run = run(AgentRunStatus.WAITING_INPUT);
        AgentInteractionEntity interaction = questionInteraction();
        prepareLocks(run, interaction);
        when(stateMachine.transitionLocked(eq(run),
                eq(EnumSet.of(AgentRunStatus.WAITING_INPUT)), eq(AgentRunStatus.RUNNING),
                isNull())).thenReturn(true);

        AgentInteractionResolutionService.PreparedQuestion prepared = service.resolveQuestion(
                1L, INTERACTION_ID,
                new AgentQuestionAnswerRequest(List.of("3"), "  추가 설명  "));

        assertThat(prepared.response().at("/selectedOptionIds/0").textValue()).isEqualTo("3");
        assertThat(prepared.response().get("freeText").textValue()).isEqualTo("추가 설명");
        ArgumentCaptor<AgentInteractionService.Resolution> resolution =
                ArgumentCaptor.forClass(AgentInteractionService.Resolution.class);
        verify(interactionService).resolve(eq(INTERACTION_ID), resolution.capture());
        assertThat(objectMapper.readTree(resolution.getValue().responseJson()))
                .isEqualTo(prepared.response());
    }

    private void prepareLocks(AgentRunEntity run, AgentInteractionEntity interaction) {
        when(interactionRepository.findById(INTERACTION_ID)).thenReturn(Optional.of(interaction));
        when(runRepository.findByIdForUpdate(RUN_ID)).thenReturn(Optional.of(run));
        when(interactionRepository.findByIdForUpdate(INTERACTION_ID))
                .thenReturn(Optional.of(interaction));
    }

    private AgentRunEntity run(AgentRunStatus status) {
        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-public-1")
                .conversationId(CONVERSATION_ID)
                .userId(1L)
                .goal("업무 처리")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(run, "id", RUN_ID);
        ReflectionTestUtils.setField(run, "status", status);
        ReflectionTestUtils.setField(run, "lastEventSeq", 5L);
        return run;
    }

    private AgentInteractionEntity approvalInteraction() {
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(RUN_ID)
                .kind(AgentInteractionKind.APPROVAL)
                .label("할 일 저장")
                .payloadJson("{\"alternatives\":[{\"id\":\"ALWAYS\"}]}")
                .toolCallId("call-1")
                .tool("task.create")
                .access(AgentAccessType.WRITE)
                .previewText("미리보기")
                .paramsCanonical("{\"taskId\":7}")
                .paramsHash("hash")
                .autoApproved(false)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        ReflectionTestUtils.setField(interaction, "id", INTERACTION_ID);
        return interaction;
    }

    private AgentInteractionEntity questionInteraction() {
        AgentInteractionEntity interaction = AgentInteractionEntity.builder()
                .runId(RUN_ID)
                .kind(AgentInteractionKind.QUESTION)
                .label("프로젝트 선택")
                .payloadJson("{\"options\":[{\"id\":\"3\",\"label\":\"A\"}],"
                        + "\"multiple\":false,\"allowFreeText\":true}")
                .questionText("어느 프로젝트인가요?")
                .autoApproved(false)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        ReflectionTestUtils.setField(interaction, "id", INTERACTION_ID);
        return interaction;
    }

    private AgentConversationEntity conversation() {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(false)
                .build();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        return conversation;
    }
}
