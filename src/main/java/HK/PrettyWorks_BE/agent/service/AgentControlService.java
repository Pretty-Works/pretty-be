package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.dto.res.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationReadResponse;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentControlService {
    private final AgentAccessGuard accessGuard;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunEventService runEventService;
    private final AgentSegmentExecutor segmentExecutor;
    private final AgentStreamService streamService;
    private final CurrentUserService currentUserService;

    public AgentControlService(AgentAccessGuard accessGuard,
                               AgentRunRepository runRepository,
                               AgentMessageRepository messageRepository,
                               AgentRunEventService runEventService,
                               AgentSegmentExecutor segmentExecutor,
                               AgentStreamService streamService,
                               CurrentUserService currentUserService) {
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.runEventService = runEventService;
        this.segmentExecutor = segmentExecutor;
        this.streamService = streamService;
        this.currentUserService = currentUserService;
    }

    public AgentStartedStream reconnect(Long userId, String publicRunId, String lastEventId) {
        currentUserService.getEmployedUser(userId);
        SseEmitter emitter = streamService.connect(userId, publicRunId, lastEventId);
        return new AgentStartedStream(publicRunId, emitter);
    }

    public AgentCancelResponse cancel(Long userId, String publicRunId) {
        currentUserService.getEmployedUser(userId);
        AgentRunEntity owned = accessGuard.run(publicRunId, userId);
        AgentRunEventService.HandlingResult result = runEventService.cancelActiveRun(owned.getId());
        if (result.disposition() == AgentRunEventService.Disposition.FAILED) {
            // DB에서 취소와 토큰 폐기를 먼저 커밋해야, 늦게 도착한 WRITE가 연결 종료보다
            // 앞서더라도 승인 검증에서 차단된다.
            segmentExecutor.cancel(owned.getId(), publicRunId);
        }
        AgentRunEntity current = runRepository.findById(owned.getId())
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        return new AgentCancelResponse(publicRunId, current.getStatus(),
                result.disposition() == AgentRunEventService.Disposition.FAILED);
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
}
