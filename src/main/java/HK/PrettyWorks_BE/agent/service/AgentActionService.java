package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentActionStatus;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.dto.req.AgentActionStatusRequest;
import HK.PrettyWorks_BE.agent.dto.res.AgentActionResponse;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 승인 대기 중인 제안을 확정한다. [승인]과 [취소]가 같은 메서드를 쓴다.
//
// 상태만 바꾸는 짧은 작업이라 평범한 @Transactional로 충분하다.
// (AgentChatService가 트랜잭션을 직접 잡는 건 외부 호출을 트랜잭션 밖에 두기 위해서고, 여기는 그럴 일이 없다)
@Service
@RequiredArgsConstructor
public class AgentActionService {

    private final AgentMessageRepository messageRepository;
    private final AgentConversationRepository conversationRepository;
    private final CurrentUserService currentUserService;
    private final AgentJsonSupport json;

    @Transactional
    public AgentActionResponse resolve(Long userId, Long messageId, AgentActionStatusRequest request) {
        currentUserService.getEmployedUser(userId);

        // PENDING은 시작 상태라 전이 대상이 아니다. enum으로 받으므로 여기서 걸러낸다.
        if (!request.status().isResolution()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        AgentMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.MESSAGE_NOT_FOUND));

        // 메시지에는 user_id가 없어 스레드를 거쳐 소유권을 확인한다.
        // 이걸 빼면 messageId만 바꿔 남의 제안을 승인하고 formData를 빼낼 수 있다.
        AgentConversationEntity conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));

        if (!conversation.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_CONVERSATION);
        }

        // 이미 확정됐거나(다른 창에서 눌렀거나) 애초에 승인 대상이 아닌 메시지.
        if (!message.isPending()) {
            throw BaseException.type(AgentErrorCode.ACTION_ALREADY_RESOLVED);
        }

        message.resolveAction(request.status());

        // 승인일 때만 화면에 채울 값을 돌려준다. 취소는 돌려줄 게 없다.
        return AgentActionResponse.builder()
                .messageId(message.getId())
                .status(request.status())
                .action(request.status() == AgentActionStatus.APPROVED
                        ? json.read(message.getActionJson())
                        : null)
                .build();
    }
}
