package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControlServiceTest {
    private final AgentAccessGuard accessGuard = mock(AgentAccessGuard.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentRunEventService runEventService = mock(AgentRunEventService.class);
    private final AgentSegmentExecutor segmentExecutor = mock(AgentSegmentExecutor.class);
    private final AgentStreamService streamService = mock(AgentStreamService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentControlService service = new AgentControlService(
            accessGuard, runRepository, runEventService, segmentExecutor,
            streamService, currentUserService);

    @Test
    void reconnectRevalidatesEmploymentAndDelegatesReplayCursor() {
        SseEmitter emitter = new SseEmitter();
        when(streamService.connect(1L, "run-public-1", "7")).thenReturn(emitter);

        AgentStartedStream result = service.reconnect(1L, "run-public-1", "7");

        assertThat(result.emitter()).isSameAs(emitter);
        verify(currentUserService).getEmployedUser(1L);
        verify(streamService).connect(1L, "run-public-1", "7");
    }

    @Test
    void cancelCommitsTerminalStateBeforeClosingFastApiSegment() {
        AgentRunEntity run = run();
        when(accessGuard.run("run-public-1", 1L)).thenReturn(run);
        when(runEventService.cancelActiveRun(10L)).thenAnswer(invocation -> {
            run.transition(AgentRunStatus.FAILED, "AGENT_027", LocalDateTime.now());
            return new AgentRunEventService.HandlingResult(
                    AgentRunEventService.Disposition.FAILED, null);
        });
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        var response = service.cancel(1L, "run-public-1");

        assertThat(response.canceled()).isTrue();
        assertThat(response.status()).isEqualTo(AgentRunStatus.FAILED);
        InOrder order = inOrder(runEventService, segmentExecutor);
        order.verify(runEventService).cancelActiveRun(10L);
        order.verify(segmentExecutor).cancel(10L, "run-public-1");
    }

    private AgentRunEntity run() {
        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-public-1")
                .conversationId(20L)
                .userId(1L)
                .goal("업무 처리")
                .startedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(run, "id", 10L);
        return run;
    }
}
