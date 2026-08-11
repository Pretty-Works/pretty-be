package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.api.response.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationDeleteResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationReadResponse;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 대화 자체의 상태를 바꾼다 — 읽음 지점과 자동 승인, 그리고 삭제. 조회는 AgentQueryService가,
// 실행 재연결·취소는 AgentControlService가 맡는다.
@Service
public class AgentConversationService {
    private final AgentAccessGuard accessGuard;
    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final CurrentUserService currentUserService;

    public AgentConversationService(AgentAccessGuard accessGuard,
                                    AgentConversationRepository conversationRepository,
                                    AgentMessageRepository messageRepository,
                                    AgentRunRepository runRepository,
                                    CurrentUserService currentUserService) {
        this.accessGuard = accessGuard;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AgentAutoApproveResponse changeAutoApprove(Long userId, Long conversationId,
                                                      boolean autoApprove) {
        currentUserService.getEmployedUser(userId);
        AgentConversationEntity conversation =
                accessGuard.conversationForUpdate(conversationId, userId);
        conversation.changeAutoApprove(autoApprove);
        return new AgentAutoApproveResponse(conversationId, conversation.isAutoApprove());
    }

    // 대화를 열었을 때(또는 스트림이 끝난 뒤) 그 시점의 마지막 말풍선까지 읽음으로 옮긴다.
    // 조회(getMessages)에 끼워 넣지 않은 이유는 두 가지다 —
    // 대화 상세 조회는 readOnly라 쓰기가 섞이면 안 되고, 라이브로 SSE를 보는 동안에는
    // 상세를 다시 부르지 않아 답변을 눈앞에서 보고도 안 읽음으로 남기 때문이다.
    @Transactional
    public AgentConversationReadResponse markRead(Long userId, Long conversationId) {
        currentUserService.getEmployedUser(userId);
        AgentConversationEntity conversation =
                accessGuard.conversationForUpdate(conversationId, userId);
        // 잠근 뒤에 읽는다. 먼저 읽으면 그 사이 도착한 답변까지 읽음으로 덮을 수 있다.
        conversation.markRead(messageRepository.findLatestId(conversationId));
        return new AgentConversationReadResponse(conversationId, conversation.getLastReadMessageId());
    }

    // 대화 하나를 지운다. 소프트 삭제라 deleted_at 만 채우고 말풍선·실행 이력은 그대로 남긴다 —
    // 화면에서만 사라진다. 조회 제외는 @SQLRestriction 이 하므로 여기서 할 일이 없다.
    //
    // 잠근 뒤에 판단한다. 아래 "진행 중 실행" 검사와 삭제 사이에 새 실행이 끼어들지 못하게 하려면
    // AgentRunFactory 가 잡는 것과 같은 행을 먼저 잡고 있어야 한다.
    // 이미 지워진 대화는 @SQLRestriction 때문에 조회 단계에서 걸리지 않아 404가 된다.
    @Transactional
    public AgentConversationDeleteResponse deleteConversation(Long userId, Long conversationId) {
        currentUserService.getEmployedUser(userId);
        AgentConversationEntity conversation =
                accessGuard.conversationForUpdate(conversationId, userId);

        // 진행 중인 실행이 있으면 아직 못 지운다. 소프트 삭제라 행이 깨지지는 않지만,
        // 목록에서 사라진 채로 실행이 계속 돌면 사용자당 3건 한도만 갉아먹고 취소할 길이 없다.
        // 대기 중이던 승인 카드도 홈에서 함께 사라져 응답할 방법이 없어진다.
        // 먼저 취소하거나 응답하라고 돌려보낸다.
        if (runRepository.existsByConversationIdAndStatusIn(
                conversationId, AgentRunStatus.activeStatuses())) {
            throw BaseException.type(AgentErrorCode.RUN_IN_PROGRESS);
        }

        // 엔티티의 @SQLDelete 가 이 호출을 deleted_at UPDATE 로 바꿔친다.
        conversationRepository.delete(conversation);
        return new AgentConversationDeleteResponse(conversationId);
    }
}
