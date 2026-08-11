package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.execution.application.AgentRunEventService;
import HK.PrettyWorks_BE.agent.execution.application.AgentSegmentExecutor;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResumeServiceTest {
    private final AgentInteractionResolutionService resolutionService =
            mock(AgentInteractionResolutionService.class);
    private final ApprovalTokenService tokenService = mock(ApprovalTokenService.class);
    private final AgentStreamService streamService = mock(AgentStreamService.class);
    private final AgentSegmentExecutor segmentExecutor = mock(AgentSegmentExecutor.class);
    private final AgentRunEventService runEventService = mock(AgentRunEventService.class);
    private final AgentResumeService service = new AgentResumeService(
            resolutionService, tokenService, streamService, segmentExecutor, runEventService);

    @Test
    void approvedResumeIssuesTokenAndStartsAfterTheWaitingEvent() {
        AgentApprovalRequest request = new AgentApprovalRequest(
                AgentDecision.APPROVED, null, null);
        when(resolutionService.resolveApproval(1L, 30L, request)).thenReturn(
                new AgentInteractionResolutionService.PreparedApproval(
                        10L, "run-public-1", 30L, AgentDecision.APPROVED,
                        null, null, true, "{\"taskId\":7}", 5L));
        when(tokenService.issue(30L)).thenReturn(
                new ApprovalTokenService.IssuedToken(
                        "secret-token", LocalDateTime.now().plusMinutes(10)));
        SseEmitter emitter = new SseEmitter();
        when(streamService.connect(1L, "run-public-1", "5")).thenReturn(emitter);

        AgentStartedStream started = service.resumeApproval(1L, 30L, request);

        assertThat(started.runId()).isEqualTo("run-public-1");
        assertThat(started.emitter()).isSameAs(emitter);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<AgentResumeRequest>> supplier =
                ArgumentCaptor.forClass(Supplier.class);
        verify(segmentExecutor).submitResume(eq(10L), eq("run-public-1"), supplier.capture());
        AgentResumeRequest resume = supplier.getValue().get();
        assertThat(resume.decision()).isEqualTo(AgentDecision.APPROVED);
        assertThat(resume.approvalToken()).isEqualTo("secret-token");
        assertThat(resume.paramsCanonical()).isEqualTo("{\"taskId\":7}");
        assertThat(resume.toString()).doesNotContain("secret-token", "taskId");
    }

    @Test
    void expirationCleanupFailureDoesNotReplaceTheOriginalClientError() {
        AgentApprovalRequest request = new AgentApprovalRequest(
                AgentDecision.APPROVED, null, null);
        BaseException expired = BaseException.type(AgentErrorCode.INTERACTION_EXPIRED);
        when(resolutionService.resolveApproval(1L, 30L, request)).thenThrow(expired);
        when(runEventService.expireInteraction(eq(30L), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.resumeApproval(1L, 30L, request))
                .isSameAs(expired);
        verify(runEventService).expireInteraction(eq(30L), any(LocalDateTime.class));
    }
}
