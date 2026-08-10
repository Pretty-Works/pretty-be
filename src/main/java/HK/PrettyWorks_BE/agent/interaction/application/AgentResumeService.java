package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.execution.application.AgentRunEventService;
import HK.PrettyWorks_BE.agent.execution.application.AgentSegmentExecutor;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Slf4j
@Service
public class AgentResumeService {
    private final AgentInteractionResolutionService resolutionService;
    private final ApprovalTokenService tokenService;
    private final AgentStreamService streamService;
    private final AgentSegmentExecutor segmentExecutor;
    private final AgentRunEventService runEventService;

    public AgentResumeService(AgentInteractionResolutionService resolutionService,
                              ApprovalTokenService tokenService,
                              AgentStreamService streamService,
                              AgentSegmentExecutor segmentExecutor,
                              AgentRunEventService runEventService) {
        this.resolutionService = resolutionService;
        this.tokenService = tokenService;
        this.streamService = streamService;
        this.segmentExecutor = segmentExecutor;
        this.runEventService = runEventService;
    }

    public AgentStartedStream resumeApproval(Long userId, Long approvalId,
                                             AgentApprovalRequest request) {
        AgentInteractionResolutionService.PreparedApproval prepared;
        try {
            prepared = resolutionService.resolveApproval(userId, approvalId, request);
        } catch (BaseException failure) {
            expireIfNeeded(approvalId, failure);
            throw failure;
        }
        try {
            Supplier<AgentResumeRequest> resume = approvalResumeSupplier(prepared);
            return connectAndSubmit(userId, prepared.internalRunId(), prepared.publicRunId(),
                    prepared.lastEventSequence(), resume);
        } catch (RuntimeException failure) {
            segmentExecutor.failRun(prepared.internalRunId(), failure);
            throw failure;
        }
    }

    public AgentStartedStream resumeQuestion(Long userId, Long questionId,
                                             AgentQuestionAnswerRequest request) {
        AgentInteractionResolutionService.PreparedQuestion prepared;
        try {
            prepared = resolutionService.resolveQuestion(userId, questionId, request);
        } catch (BaseException failure) {
            expireIfNeeded(questionId, failure);
            throw failure;
        }
        try {
            Supplier<AgentResumeRequest> resume = () -> AgentResumeRequest.question(
                    prepared.questionId(), prepared.response());
            return connectAndSubmit(userId, prepared.internalRunId(), prepared.publicRunId(),
                    prepared.lastEventSequence(), resume);
        } catch (RuntimeException failure) {
            segmentExecutor.failRun(prepared.internalRunId(), failure);
            throw failure;
        }
    }

    private AgentStartedStream connectAndSubmit(Long userId, Long internalRunId,
                                                String publicRunId, long afterSequence,
                                                Supplier<AgentResumeRequest> request) {
        SseEmitter emitter = streamService.connect(
                userId, publicRunId, Long.toString(afterSequence));
        segmentExecutor.submitResume(internalRunId, publicRunId, request);
        return new AgentStartedStream(publicRunId, emitter);
    }

    private Supplier<AgentResumeRequest> approvalResumeSupplier(
            AgentInteractionResolutionService.PreparedApproval prepared) {
        return () -> {
            String token = prepared.tokenRequired()
                    ? tokenService.issue(prepared.approvalId()).token() : null;
            return AgentResumeRequest.approval(
                    prepared.approvalId(), prepared.decision(), prepared.alternativeId(),
                    prepared.reason(), token, prepared.paramsCanonical());
        };
    }

    private void expireIfNeeded(Long interactionId, BaseException failure) {
        if (failure.getCode() == AgentErrorCode.INTERACTION_EXPIRED) {
            try {
                runEventService.expireInteraction(interactionId, LocalDateTime.now());
            } catch (RuntimeException expirationFailure) {
                // 만료 확정은 다음 스케줄러 실행에서도 복구할 수 있다. 정리 실패가 사용자가
                // 받아야 할 원래 AGENT_019를 5xx로 바꾸지 않도록 여기서 경계를 끊는다.
                log.error("[에이전트 상호작용 만료 확정 실패] interactionId={}",
                        interactionId, expirationFailure);
            }
        }
    }
}
