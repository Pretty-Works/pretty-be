package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.constant.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.repository.AgentPendingInteractionRow;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentQueryServiceTest {
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentMessageStepRepository stepRepository =
            mock(AgentMessageStepRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentInteractionRepository interactionRepository =
            mock(AgentInteractionRepository.class);
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentQueryService service = new AgentQueryService(
            conversationRepository, messageRepository, stepRepository, runRepository,
            interactionRepository, accessGuard, new AgentJsonSupport(new ObjectMapper()),
            currentUserService);

    @Test
    void conversationProjectionIncludesActiveRunAndAutoApproveState() {
        AgentConversationEntity conversation = conversation(true);
        AgentRunEntity run = run(AgentRunStatus.WAITING_INPUT);
        when(conversationRepository.findByUserIdOrderByLastMessageAtDesc(
                eq(1L), any(Pageable.class))).thenReturn(List.of(conversation));
        when(runRepository.findByConversationIdInAndStatusIn(
                eq(List.of(20L)), any(Collection.class))).thenReturn(List.of(run));

        var response = service.getConversations(1L, 20);

        assertThat(response.conversations()).singleElement().satisfies(item -> {
            assertThat(item.autoApprove()).isTrue();
            assertThat(item.activeRunId()).isEqualTo("run-public-1");
            assertThat(item.activeRunStatus()).isEqualTo(AgentRunStatus.WAITING_INPUT);
        });
        verify(currentUserService).getEmployedUser(1L);
    }

    @Test
    void pendingProjectionReturnsRenderablePayloadAndBadgeCount() {
        LocalDateTime now = LocalDateTime.now();
        when(interactionRepository.findPendingRows(eq(1L),
                eq(AgentInteractionStatus.PENDING), any(Collection.class),
                any(LocalDateTime.class))).thenReturn(List.of(
                new AgentPendingInteractionRow(30L, AgentInteractionKind.APPROVAL,
                        "할 일 저장", "{\"approvalId\":30}", "run-public-1", 20L,
                        "대화", now.plusMinutes(30), now)));

        var response = service.getPendingInteractions(1L);

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.interactions()).singleElement().satisfies(item -> {
            assertThat(item.interactionId()).isEqualTo(30L);
            assertThat(item.payload().path("approvalId").asLong()).isEqualTo(30L);
        });
    }

    private AgentConversationEntity conversation(boolean autoApprove) {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(autoApprove)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 20L);
        return conversation;
    }

    private AgentRunEntity run(AgentRunStatus status) {
        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-public-1")
                .conversationId(20L)
                .userId(1L)
                .goal("업무 처리")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(run, "id", 10L);
        ReflectionTestUtils.setField(run, "status", status);
        return run;
    }
}
