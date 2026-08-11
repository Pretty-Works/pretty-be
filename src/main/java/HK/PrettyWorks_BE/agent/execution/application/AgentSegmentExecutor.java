package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.domain.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.execution.gateway.AgentServerClient;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
public class AgentSegmentExecutor {
    private final AgentServerClient serverClient;
    private final AgentRunEventService runEventService;
    private final AgentRunRepository runRepository;
    private final ThreadPoolExecutor executionPool;
    private final ConcurrentMap<Long, AtomicInteger> scheduledRuns = new ConcurrentHashMap<>();

    public AgentSegmentExecutor(
            AgentServerClient serverClient,
            AgentRunEventService runEventService,
            AgentRunRepository runRepository,
            @Value("${agent.execution.threads:4}") int executionThreads,
            @Value("${agent.execution.queue-capacity:100}") int queueCapacity
    ) {
        if (executionThreads <= 0 || queueCapacity <= 0) {
            throw new IllegalStateException("agent execution settings must be positive");
        }
        this.serverClient = serverClient;
        this.runEventService = runEventService;
        this.runRepository = runRepository;
        this.executionPool = new ThreadPoolExecutor(
                executionThreads, executionThreads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), daemonFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void submitStart(Long internalRunId, String publicRunId, AgentRunRequest request) {
        submit(internalRunId, publicRunId,
                consumer -> serverClient.startRun(request, consumer));
    }

    public void submitResume(Long internalRunId, String publicRunId, AgentResumeRequest request) {
        submitResume(internalRunId, publicRunId, () -> request);
    }

    public void submitResume(Long internalRunId, String publicRunId,
                             Supplier<AgentResumeRequest> requestSupplier) {
        submit(internalRunId, publicRunId,
                consumer -> serverClient.resumeRun(
                        publicRunId, requestSupplier.get(), consumer));
    }

    public void failRun(Long internalRunId, Throwable failure) {
        AgentErrorCode code = failure instanceof BaseException base
                && base.getCode() instanceof AgentErrorCode agentCode
                ? agentCode : AgentErrorCode.AGENT_RESPONSE_INVALID;
        failSafely(internalRunId, code, failure);
    }

    public void cancel(Long internalRunId, String publicRunId) {
        if (scheduledRuns.containsKey(internalRunId)) {
            serverClient.cancelRun(publicRunId);
        }
    }

    private void submit(Long internalRunId, String publicRunId, SegmentCall firstCall) {
        scheduledRuns.computeIfAbsent(internalRunId, ignored -> new AtomicInteger())
                .incrementAndGet();
        try {
            executionPool.execute(() -> {
                try {
                    consume(internalRunId, publicRunId, firstCall);
                } finally {
                    scheduledRuns.computeIfPresent(internalRunId, (ignored, count) ->
                            count.decrementAndGet() == 0 ? null : count);
                }
            });
        } catch (RejectedExecutionException overloaded) {
            scheduledRuns.computeIfPresent(internalRunId, (ignored, count) ->
                    count.decrementAndGet() == 0 ? null : count);
            failSafely(internalRunId, AgentErrorCode.RATE_LIMITED, overloaded);
        }
    }

    private void consume(Long internalRunId, String publicRunId, SegmentCall firstCall) {
        SegmentCall call = firstCall;
        try {
            while (true) {
                if (!isRunning(internalRunId)) {
                    return;
                }
                AtomicReference<AgentRunEventService.HandlingResult> lastResult =
                        new AtomicReference<>();
                AgentSegmentOutcome outcome = call.execute(event -> {
                    AgentRunEventService.HandlingResult handled =
                            runEventService.handle(internalRunId, event);
                    lastResult.set(handled);
                    // Redis 취소 신호가 유실돼도 다음 FastAPI 이벤트에서 종료 상태를 발견하면
                    // 이 노드가 직접 응답 body를 닫는다. 취소된 실행의 스트림을 10분간 끌지 않는다.
                    if (handled.disposition() == AgentRunEventService.Disposition.IGNORED
                            && !isRunning(internalRunId)) {
                        throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
                    }
                });
                AgentRunEventService.HandlingResult applied = lastResult.get();
                if (outcome != AgentSegmentOutcome.WAITING_APPROVAL
                        || applied == null
                        || applied.disposition() != AgentRunEventService.Disposition.AUTO_APPROVAL) {
                    return;
                }

                AgentRunEventService.AutoApprovalResume approval = applied.autoApproval();
                AgentResumeRequest resume = AgentResumeRequest.approval(
                        approval.approvalId(), AgentDecision.APPROVED, null, null,
                        approval.approvalToken(), approval.paramsCanonical());
                call = consumer -> serverClient.resumeRun(publicRunId, resume, consumer);
            }
        } catch (BaseException failure) {
            AgentErrorCode code = failure.getCode() instanceof AgentErrorCode agentCode
                    ? agentCode : AgentErrorCode.AGENT_RESPONSE_INVALID;
            failSafely(internalRunId, code, failure);
        } catch (RuntimeException failure) {
            failSafely(internalRunId, AgentErrorCode.AGENT_RESPONSE_INVALID, failure);
        }
    }

    private boolean isRunning(Long internalRunId) {
        return runRepository.findById(internalRunId)
                .map(run -> run.getStatus() == AgentRunStatus.RUNNING)
                .orElse(false);
    }

    private void failSafely(Long internalRunId, AgentErrorCode errorCode, Throwable cause) {
        try {
            runEventService.failRunningRun(internalRunId, errorCode);
        } catch (RuntimeException terminalFailure) {
            log.error("[에이전트 실행 종료 실패] internalRunId={} original={}",
                    internalRunId, cause.getClass().getSimpleName(), terminalFailure);
        }
    }

    private ThreadFactory daemonFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "agent-fastapi-execution-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        executionPool.shutdownNow();
    }

    @FunctionalInterface
    private interface SegmentCall {
        AgentSegmentOutcome execute(Consumer<DecodedAgentServerEvent> consumer);
    }
}
