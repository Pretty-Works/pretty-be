package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.domain.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.execution.gateway.AgentServerClient;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSegmentExecutorTest {
    private final AgentServerClient serverClient = mock(AgentServerClient.class);
    private final AgentRunEventService runEventService = mock(AgentRunEventService.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentSegmentExecutor executor =
            new AgentSegmentExecutor(serverClient, runEventService, runRepository, 1, 4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void autoApprovalContinuesWithResumeOnTheSameWorker() throws Exception {
        AgentRunEntity running = mock(AgentRunEntity.class);
        when(running.getStatus()).thenReturn(AgentRunStatus.RUNNING);
        when(runRepository.findById(10L)).thenReturn(Optional.of(running));
        AgentRunEventService.AutoApprovalResume credentials = autoApprovalCredentials();
        when(runEventService.handle(eq(10L), any())).thenReturn(
                new AgentRunEventService.HandlingResult(
                        AgentRunEventService.Disposition.AUTO_APPROVAL, credentials),
                new AgentRunEventService.HandlingResult(
                        AgentRunEventService.Disposition.COMPLETED, null));
        when(serverClient.startRun(any(), any())).thenAnswer(invocation -> {
            Consumer<DecodedAgentServerEvent> consumer = invocation.getArgument(1);
            consumer.accept(approvalEvent());
            return AgentSegmentOutcome.WAITING_APPROVAL;
        });
        CountDownLatch resumed = new CountDownLatch(1);
        when(serverClient.resumeRun(eq("run-public-1"), any(), any())).thenAnswer(invocation -> {
            Consumer<DecodedAgentServerEvent> consumer = invocation.getArgument(2);
            consumer.accept(doneEvent());
            resumed.countDown();
            return AgentSegmentOutcome.COMPLETED;
        });

        AgentRunRequest request = new AgentRunRequest("run-public-1", 10L, "업무 처리",
                java.util.List.of(), objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"),
                "WEB", "ko-KR", java.util.List.of());
        executor.submitStart(10L, "run-public-1", request);

        assertThat(resumed.await(2, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<AgentResumeRequest> resume =
                ArgumentCaptor.forClass(AgentResumeRequest.class);
        verify(serverClient).resumeRun(eq("run-public-1"), resume.capture(), any());
        assertThat(resume.getValue().approvalToken()).isEqualTo("secret-token");
        assertThat(resume.getValue().paramsCanonical()).isEqualTo("{\"taskId\":7}");
    }

    private AgentRunEventService.AutoApprovalResume autoApprovalCredentials() throws Exception {
        Constructor<AgentRunEventService.AutoApprovalResume> constructor =
                AgentRunEventService.AutoApprovalResume.class.getDeclaredConstructor(
                        Long.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(30L, "secret-token", "{\"taskId\":7}");
    }

    private DecodedAgentServerEvent approvalEvent() {
        var payload = objectMapper.createObjectNode();
        payload.put("toolCallId", "call-1");
        return new DecodedAgentServerEvent(
                new AgentServerEvent.Step("승인 처리"), payload);
    }

    private DecodedAgentServerEvent doneEvent() {
        var payload = objectMapper.createObjectNode();
        payload.put("answer", "완료");
        payload.putNull("action");
        return new DecodedAgentServerEvent(new AgentServerEvent.Done("완료", null), payload);
    }
}
