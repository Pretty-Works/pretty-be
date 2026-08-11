package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationServiceTest {
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);
    private final AgentConversationRepository conversationRepository =
            mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentConversationService service = new AgentConversationService(
            accessGuard, conversationRepository, messageRepository, runRepository,
            currentUserService);

    @Test
    void autoApproveCanBeTurnedOffThroughOwnedConversation() {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(true)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 20L);
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);

        var response = service.changeAutoApprove(1L, 20L, false);

        assertThat(response.autoApprove()).isFalse();
        assertThat(conversation.isAutoApprove()).isFalse();
        verify(currentUserService).getEmployedUser(1L);
    }

    @Test
    void markReadMovesReadPointToLatestMessage() {
        AgentConversationEntity conversation = conversation();
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);
        when(messageRepository.findLatestId(20L)).thenReturn(102L);

        var response = service.markRead(1L, 20L);

        assertThat(response.conversationId()).isEqualTo(20L);
        assertThat(response.lastReadMessageId()).isEqualTo(102L);
        assertThat(conversation.getLastReadMessageId()).isEqualTo(102L);
        verify(currentUserService).getEmployedUser(1L);
    }

    // 늦게 도착한 읽음 요청이 이미 읽은 답변을 다시 안 읽음으로 되돌리면 안 된다.
    @Test
    void markReadNeverMovesReadPointBackwards() {
        AgentConversationEntity conversation = conversation();
        ReflectionTestUtils.setField(conversation, "lastReadMessageId", 102L);
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);
        when(messageRepository.findLatestId(20L)).thenReturn(97L);

        var response = service.markRead(1L, 20L);

        assertThat(response.lastReadMessageId()).isEqualTo(102L);
    }

    // 말풍선이 하나도 없는 대화도 404 없이 지나가야 한다 — 옮길 지점만 없다.
    @Test
    void markReadOnEmptyConversationKeepsNullReadPoint() {
        AgentConversationEntity conversation = conversation();
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);
        when(messageRepository.findLatestId(20L)).thenReturn(null);

        var response = service.markRead(1L, 20L);

        assertThat(response.lastReadMessageId()).isNull();
    }

    // 삭제는 repository.delete() 한 번이다. 엔티티의 @SQLDelete 가 그것을 deleted_at UPDATE 로
    // 바꿔치므로 서비스는 자식(말풍선·실행·이벤트)을 건드릴 일이 없다.
    @Test
    void deleteSoftDeletesTheOwnedConversation() {
        AgentConversationEntity conversation = conversation();
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);
        when(runRepository.existsByConversationIdAndStatusIn(
                20L, AgentRunStatus.activeStatuses())).thenReturn(false);

        var response = service.deleteConversation(1L, 20L);

        assertThat(response.conversationId()).isEqualTo(20L);
        verify(conversationRepository).delete(conversation);
        verify(currentUserService).getEmployedUser(1L);
    }

    // 진행 중인 실행이 걸려 있으면 409로 막는다. 소프트 삭제라 행이 깨지지는 않지만, 목록에서
    // 사라진 채로 실행이 계속 돌면 사용자당 3건 한도만 갉아먹고 취소할 길이 없다.
    @Test
    void deleteRejectsAConversationWithARunInProgress() {
        AgentConversationEntity conversation = conversation();
        when(accessGuard.conversationForUpdate(20L, 1L)).thenReturn(conversation);
        when(runRepository.existsByConversationIdAndStatusIn(
                20L, AgentRunStatus.activeStatuses())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteConversation(1L, 20L))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AgentErrorCode.RUN_IN_PROGRESS));
        verify(conversationRepository, never()).delete(any());
    }

    // 소유권과 "이미 지워진 대화"(@SQLRestriction 때문에 조회에 안 잡힘)는 둘 다 가드가 판정한다.
    // 가드가 막으면 삭제까지 가지 않아야 한다.
    @Test
    void deleteStopsAtTheAccessGuardWithoutTouchingTheRow() {
        when(accessGuard.conversationForUpdate(20L, 1L))
                .thenThrow(BaseException.type(AgentErrorCode.NOT_MY_CONVERSATION));

        assertThatThrownBy(() -> service.deleteConversation(1L, 20L))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(AgentErrorCode.NOT_MY_CONVERSATION));
        verify(conversationRepository, never()).delete(any());
    }

    private AgentConversationEntity conversation() {
        AgentConversationEntity conversation = AgentConversationEntity.builder()
                .userId(1L)
                .title("대화")
                .lastMessageAt(LocalDateTime.now())
                .autoApprove(false)
                .build();
        ReflectionTestUtils.setField(conversation, "id", 20L);
        return conversation;
    }
}
