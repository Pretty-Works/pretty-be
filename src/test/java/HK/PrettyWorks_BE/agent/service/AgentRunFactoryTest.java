package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunFactoryTest {
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);

    private final AgentRunFactory factory = new AgentRunFactory(currentUserService,
            conversationRepository, runRepository, messageRepository, accessGuard);

    @Test
    void createsRunAndUserMessageUnderTheSameConversation() {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("기존 대화")
                .lastMessageAt(LocalDateTime.now().minusMinutes(1))
                .autoApprove(false)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 10L);
        when(accessGuard.conversationForUpdate(10L, 1L)).thenReturn(conversation);
        when(runRepository.existsByConversationIdAndStatusIn(anyLong(), any()))
                .thenReturn(false);
        when(runRepository.countByUserIdAndStatusIn(anyLong(), any())).thenReturn(0L);
        when(runRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> {
            AgentRunEntity run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 20L);
            return run;
        });
        when(messageRepository.save(any(AgentMessageEntity.class))).thenAnswer(invocation -> {
            AgentMessageEntity message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 30L);
            return message;
        });

        AgentRunFactory.StartedRun started = factory.start(
                1L, 10L, "이번 주 업무 정리", "{\"screen\":\"TASK_LIST\"}", "session-1");

        ArgumentCaptor<AgentRunEntity> run = ArgumentCaptor.forClass(AgentRunEntity.class);
        ArgumentCaptor<AgentMessageEntity> message =
                ArgumentCaptor.forClass(AgentMessageEntity.class);
        verify(runRepository).save(run.capture());
        verify(messageRepository).save(message.capture());
        assertThat(run.getValue().getConversationId()).isEqualTo(10L);
        assertThat(run.getValue().getSessionId()).isEqualTo("session-1");
        assertThat(message.getValue().getConversationId()).isEqualTo(10L);
        assertThat(message.getValue().getRunId()).isEqualTo(20L);
        assertThat(message.getValue().getRole()).isEqualTo(AgentRole.USER);
        assertThat(started.userMessage().getId()).isEqualTo(30L);
        verify(currentUserService).getEmployedUserForUpdate(1L);
    }
}
