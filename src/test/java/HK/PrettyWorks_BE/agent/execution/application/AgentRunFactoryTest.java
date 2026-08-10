package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageAttachmentEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageAttachmentRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.shared.attachment.AgentFileEncoding;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentRunFactoryTest {
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentMessageAttachmentRepository attachmentRepository =
            mock(AgentMessageAttachmentRepository.class);
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);

    private final AgentRunFactory factory = new AgentRunFactory(currentUserService,
            conversationRepository, runRepository, messageRepository, attachmentRepository,
            accessGuard);

    @Test
    void createsRunAndUserMessageUnderTheSameConversation() {
        givenExistingConversation();

        AgentRunFactory.StartedRun started = factory.start(
                1L, 10L, "이번 주 업무 정리", "{\"screen\":\"TASK_LIST\"}", "session-1", List.of());

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
        // 첨부가 없으면 쓸 일이 없다 — 빈 saveAll 로 왕복하지 않는다.
        verifyNoInteractions(attachmentRepository);
    }

    // 첨부는 메타데이터만 남긴다. 내용은 FastAPI로 흘러갈 뿐 DB에 저장하지 않는다.
    @Test
    void savesAttachmentMetadataInTheOrderTheUserPickedThem() {
        givenExistingConversation();

        factory.start(1L, 10L, "요약해줘", "{\"screen\":\"TASK_LIST\"}", "session-1",
                List.of(attached("첫째.txt", 11L), attached("둘째.txt", 22L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentMessageAttachmentEntity>> saved =
                ArgumentCaptor.forClass(List.class);
        verify(attachmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).satisfiesExactly(
                first -> {
                    assertThat(first.getMessageId()).isEqualTo(30L);
                    assertThat(first.getSeq()).isZero();
                    assertThat(first.getFilename()).isEqualTo("첫째.txt");
                    assertThat(first.getContentType()).isEqualTo("text/plain");
                    assertThat(first.getSizeBytes()).isEqualTo(11L);
                },
                second -> {
                    assertThat(second.getSeq()).isEqualTo(1);
                    assertThat(second.getFilename()).isEqualTo("둘째.txt");
                });
    }

    private AgentRunRequest.AttachedFile attached(String filename, long sizeBytes) {
        return new AgentRunRequest.AttachedFile(
                filename, "text/plain", sizeBytes, AgentFileEncoding.TEXT, "내용");
    }

    private void givenExistingConversation() {
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
    }
}
