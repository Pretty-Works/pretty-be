package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.execution.api.response.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 이미 만들어진 실행을 바깥에서 제어한다 — 끊긴 스트림 다시 붙이기와 중단.
// 대화 자체의 상태(읽음·자동 승인)는 AgentConversationService가 맡는다.
@Service
public class AgentControlService {
    private final AgentAccessGuard accessGuard;
    private final AgentRunRepository runRepository;
    private final AgentRunEventService runEventService;
    private final AgentSegmentExecutor segmentExecutor;
    private final AgentStreamService streamService;
    private final CurrentUserService currentUserService;

    public AgentControlService(AgentAccessGuard accessGuard,
                               AgentRunRepository runRepository,
                               AgentRunEventService runEventService,
                               AgentSegmentExecutor segmentExecutor,
                               AgentStreamService streamService,
                               CurrentUserService currentUserService) {
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
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
}
