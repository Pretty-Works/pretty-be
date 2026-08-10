package HK.PrettyWorks_BE.agent.execution.streaming;

import HK.PrettyWorks_BE.agent.execution.domain.AgentEventEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentStreamService {
    private final AgentAccessGuard accessGuard;
    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final TransactionTemplate transactionTemplate;
    private final long heartbeatMillis;
    private final long segmentTimeoutMillis;
    private final int maxConnectionsPerUser;
    private final ScheduledExecutorService heartbeatScheduler;
    private final ExecutorService heartbeatSender;
    private final ExecutorService eventSender;
    private final Map<Long, Deque<Connection>> connectionsByUser = new ConcurrentHashMap<>();
    private final Map<Long, Set<Connection>> connectionsByRun = new ConcurrentHashMap<>();

    // 실행별 락을 Map에 담아두면 지울 시점이 애매하다. completeRun 에서만 지우면 실패·만료·취소로
    // 끝난 실행의 항목이 서버 수명 내내 남고, publish 끝에서 지우면 대기 중이던 스레드는 옛 락을,
    // 새 스레드는 새 락을 잡아 같은 실행의 이벤트가 동시에 발행된다.
    //
    // 고정 배열이면 지울 시점 자체가 없어진다. 서로 다른 실행이 드물게 같은 락을 공유하지만
    // 임계구역이 이벤트 저장 한 건이라 짧다.
    private static final int PUBLISH_LOCK_STRIPES = 256;
    private final Object[] publishLocks = createPublishLocks();

    // 막힌 연결 하나는 전송이 끝날 때까지 스레드 하나를 붙잡는다. 이 값이 곧
    // "동시에 막혀 있어도 나머지 연결의 하트비트가 밀리지 않는 수"다.
    private static final int HEARTBEAT_SENDER_THREADS = 4;
    private static final int HEARTBEAT_SENDER_TASKS = 1024;
    private static final int EVENT_SENDER_THREADS = 8;
    private static final int EVENT_SENDER_TASKS = 1024;
    private static final int MAX_PENDING_EVENTS_PER_CONNECTION = 128;

    public AgentStreamService(AgentAccessGuard accessGuard,
                              AgentRunRepository runRepository,
                              AgentEventRepository eventRepository,
                              PlatformTransactionManager transactionManager,
                              @Value("${agent.stream.heartbeat-millis}") long heartbeatMillis,
                              @Value("${agent.stream.segment-timeout-millis}") long segmentTimeoutMillis,
                              @Value("${agent.stream.max-connections-per-user}") int maxConnectionsPerUser) {
        if (heartbeatMillis <= 0 || segmentTimeoutMillis <= 0 || maxConnectionsPerUser <= 0) {
            throw new IllegalStateException("agent.stream settings must be positive");
        }
        this.accessGuard = accessGuard;
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.heartbeatMillis = heartbeatMillis;
        this.segmentTimeoutMillis = segmentTimeoutMillis;
        this.maxConnectionsPerUser = maxConnectionsPerUser;
        // 스케줄러는 "언제 보낼지"만 정하고, 실제 블로킹 전송은 별도 풀이 맡는다.
        // 한 스레드에서 send() 까지 하면 느린 클라이언트 하나가 전체 연결의 하트비트를 밀어버리고,
        // 그게 프록시 유휴 종료 → 대량 재접속 → 이벤트 재생 폭증으로 번진다.
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("agent-sse-heartbeat-scheduler"));
        this.heartbeatSender = new ThreadPoolExecutor(
                HEARTBEAT_SENDER_THREADS, HEARTBEAT_SENDER_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(HEARTBEAT_SENDER_TASKS),
                daemonFactory("agent-sse-heartbeat-sender"),
                new ThreadPoolExecutor.AbortPolicy());
        this.eventSender = new ThreadPoolExecutor(
                EVENT_SENDER_THREADS, EVENT_SENDER_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EVENT_SENDER_TASKS),
                daemonFactory("agent-sse-event-sender"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory daemonFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Object[] createPublishLocks() {
        Object[] locks = new Object[PUBLISH_LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    public SseEmitter connect(Long userId, String publicRunId, String lastEventId) {
        AgentRunEntity run = accessGuard.run(publicRunId, userId);
        long afterSequence = parseLastEventId(lastEventId);
        if (afterSequence > run.getLastEventSeq()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        Connection connection = new Connection(userId, run.getId(), afterSequence,
                new SseEmitter(segmentTimeoutMillis));
        registerCallbacks(connection);
        try {
            // Register before querying. Live delivery then waits on this monitor until replay is complete.
            synchronized (connection) {
                register(connection);
                List<AgentEventEntity> missed = eventRepository
                        .findByRunIdAndSeqGreaterThanOrderBySeq(run.getId(), afterSequence);
                for (AgentEventEntity event : missed) {
                    if (!sendEvent(connection, event)) {
                        throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
                    }
                }
            }
            // 수동 승인·질문 대기와 종료 Run은 저장된 마지막 이벤트까지만 재생하고 세그먼트를 닫는다.
            // 자동 승인은 Run이 RUNNING인 채 resume되므로 같은 FE 연결을 유지한다.
            AgentRunStatus currentStatus = runRepository.findById(run.getId())
                    .map(AgentRunEntity::getStatus)
                    .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
            if (currentStatus != AgentRunStatus.RUNNING) {
                close(connection, null);
                return connection.emitter;
            }
            ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                    () -> dispatchHeartbeat(connection), heartbeatMillis, heartbeatMillis,
                    TimeUnit.MILLISECONDS);
            installHeartbeat(connection, heartbeat);
            return connection.emitter;
        } catch (RuntimeException failure) {
            close(connection, null);
            throw failure;
        }
    }

    public AgentEventEntity publish(Long internalRunId, String eventType, String payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "활성 트랜잭션에서는 appendInCurrentTransaction()을 사용해야 합니다.");
        }
        validateEvent(eventType, payload);

        Object runLock = publishLocks[Math.floorMod(Long.hashCode(internalRunId), PUBLISH_LOCK_STRIPES)];
        synchronized (runLock) {
            AgentEventEntity event = transactionTemplate.execute(status ->
                    appendPersisted(internalRunId, eventType, payload));
            deliver(event);
            return event;
        }
    }

    /**
     * 호출자의 업무 트랜잭션에 이벤트 저장을 합칩니다. 반환된 이벤트는 트랜잭션이 성공해
     * 호출자에게 돌아온 뒤에만 {@link #deliver(AgentEventEntity)} 해야 합니다.
     */
    public AgentEventEntity appendInCurrentTransaction(
            Long internalRunId, String eventType, String payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("이벤트 append에는 활성 트랜잭션이 필요합니다.");
        }
        validateEvent(eventType, payload);
        return appendPersisted(internalRunId, eventType, payload);
    }

    /** 커밋이 끝난 영속 이벤트를 현재 연결들에 전달합니다. */
    public void deliver(AgentEventEntity event) {
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("커밋된 이벤트만 전달할 수 있습니다.");
        }
        publishToConnections(event);
    }

    /** Handles a cross-instance Redis wake-up by reading the committed DB tail. */
    public void deliverCommittedSignal(Long internalRunId, long signaledSequence) {
        AgentRunEntity run = runRepository.findById(internalRunId).orElse(null);
        if (run == null || run.getLastEventSeq() < signaledSequence) {
            return;
        }
        eventRepository.findByRunIdAndSeq(internalRunId, run.getLastEventSeq())
                .ifPresent(this::publishToConnections);
        if (run.getStatus() != AgentRunStatus.RUNNING) {
            completeSegment(internalRunId);
        }
    }

    private AgentEventEntity appendPersisted(Long internalRunId, String eventType, String payload) {
        AgentRunEntity run = runRepository.findByIdForUpdate(internalRunId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        long sequence = run.issueNextEventSequence(LocalDateTime.now());
        return eventRepository.save(new AgentEventEntity(
                internalRunId, sequence, eventType, payload));
    }

    private void validateEvent(String eventType, String payload) {
        if (eventType == null || eventType.isBlank() || eventType.length() > 20
                || eventType.indexOf('\n') >= 0 || eventType.indexOf('\r') >= 0
                || payload == null || payload.indexOf('\n') >= 0 || payload.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("SSE payload는 개행 없는 JSON이어야 합니다.");
        }
        int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > AgentPayloadLimits.MAX_EVENT_DATA_BYTES
                || ("step".equals(eventType)
                && payloadBytes > AgentPayloadLimits.MAX_STEP_DATA_BYTES)) {
            throw new IllegalArgumentException("SSE payload가 허용 크기를 초과했습니다.");
        }
    }

    /**
     * 실행이 끝났을 때 그 실행에 붙어 있던 SSE 연결을 모두 정리한다.
     *
     * <p><b>모든 종료 전이(COMPLETED · FAILED · EXPIRED · 취소)가 이 메서드를 거쳐야 한다.</b>
     * 한 경로라도 빠뜨리면 그 연결은 세그먼트 타임아웃(10분)까지 열린 채로 남는다.
     */
    public void completeRun(Long internalRunId) {
        completeSegment(internalRunId);
    }

    /** Run 상태를 끝내지 않고 현재 HTTP SSE 구간만 닫습니다. */
    public void completeSegment(Long internalRunId) {
        for (Connection connection : snapshotRunConnections(internalRunId)) {
            completeAfterPendingEvents(connection);
        }
    }

    private void publishToConnections(AgentEventEntity event) {
        for (Connection connection : snapshotRunConnections(event.getRunId())) {
            enqueueEvent(connection, event);
        }
    }

    private void enqueueEvent(Connection connection, AgentEventEntity event) {
        boolean schedule = false;
        boolean overflow = false;
        synchronized (connection.queueLock) {
            if (connection.closed.get()) {
                return;
            }
            if (connection.pendingEvents.size() >= MAX_PENDING_EVENTS_PER_CONNECTION) {
                overflow = true;
            } else {
                connection.pendingEvents.addLast(event);
                if (!connection.deliveryScheduled) {
                    connection.deliveryScheduled = true;
                    schedule = true;
                }
            }
        }
        if (overflow) {
            close(connection, new IllegalStateException("SSE 이벤트 전송 대기열이 가득 찼습니다."));
        } else if (schedule) {
            submitDelivery(connection);
        }
    }

    private void submitDelivery(Connection connection) {
        try {
            eventSender.execute(() -> drainEvents(connection));
        } catch (RejectedExecutionException overloadedOrStopping) {
            synchronized (connection.queueLock) {
                connection.deliveryScheduled = false;
            }
            close(connection, overloadedOrStopping);
        }
    }

    private void drainEvents(Connection connection) {
        while (!connection.closed.get()) {
            AgentEventEntity event;
            boolean closeAfterDrain;
            synchronized (connection.queueLock) {
                event = connection.pendingEvents.pollFirst();
                if (event == null) {
                    connection.deliveryScheduled = false;
                    closeAfterDrain = connection.closeAfterDrain;
                } else {
                    closeAfterDrain = false;
                }
            }
            if (event == null) {
                if (closeAfterDrain) {
                    close(connection, null);
                }
                return;
            }
            sendEventInSequence(connection, event);
        }
        synchronized (connection.queueLock) {
            connection.deliveryScheduled = false;
        }
    }

    private void completeAfterPendingEvents(Connection connection) {
        boolean closeNow;
        boolean schedule = false;
        synchronized (connection.queueLock) {
            if (connection.closed.get()) {
                return;
            }
            connection.closeAfterDrain = true;
            closeNow = connection.pendingEvents.isEmpty() && !connection.deliveryScheduled;
            if (!closeNow && !connection.deliveryScheduled) {
                connection.deliveryScheduled = true;
                schedule = true;
            }
        }
        if (closeNow) {
            close(connection, null);
        } else if (schedule) {
            submitDelivery(connection);
        }
    }

    private void sendEventInSequence(Connection connection, AgentEventEntity event) {
        synchronized (connection) {
            if (connection.closed.get() || event.getSeq() <= connection.lastSentSequence) {
                return;
            }
            if (event.getSeq() == connection.lastSentSequence + 1) {
                sendEvent(connection, event);
                return;
            }

            // Commit order and post-commit delivery order are not the same thing. If a later
            // publisher reaches this connection first, fill the committed gap before advancing
            // Last-Event-ID; otherwise the browser could never request the skipped sequence again.
            long expected = connection.lastSentSequence + 1;
            List<AgentEventEntity> ordered = eventRepository
                    .findByRunIdAndSeqGreaterThanAndSeqLessThanEqualOrderBySeq(
                            event.getRunId(), connection.lastSentSequence, event.getSeq());
            for (AgentEventEntity candidate : ordered) {
                if (candidate.getSeq() != expected || !sendEvent(connection, candidate)) {
                    close(connection, new IllegalStateException("SSE 이벤트 시퀀스가 연속적이지 않습니다."));
                    return;
                }
                expected++;
            }
            if (expected <= event.getSeq()) {
                close(connection, new IllegalStateException("SSE 이벤트 시퀀스가 누락되었습니다."));
            }
        }
    }

    private boolean sendEvent(Connection connection, AgentEventEntity event) {
        synchronized (connection) {
            if (connection.closed.get() || event.getSeq() <= connection.lastSentSequence) {
                return !connection.closed.get();
            }
            try {
                connection.emitter.send(SseEmitter.event()
                        .id(Long.toString(event.getSeq()))
                        .name(event.getEventType())
                        .data(event.getPayload(), MediaType.APPLICATION_JSON));
                connection.lastSentSequence = event.getSeq();
                return true;
            } catch (IOException | IllegalStateException exception) {
                close(connection, exception);
                return false;
            }
        }
    }

    // 앞 하트비트가 아직 전송 중이면 이번 차례는 건너뛴다. 쌓아두면 막힌 연결 하나에
    // 전송 작업이 계속 누적돼 풀을 먹는다.
    private void dispatchHeartbeat(Connection connection) {
        if (connection.closed.get() || !connection.heartbeatInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            heartbeatSender.execute(() -> {
                try {
                    sendHeartbeat(connection);
                } finally {
                    connection.heartbeatInFlight.set(false);
                }
            });
        } catch (RejectedExecutionException shuttingDown) {
            connection.heartbeatInFlight.set(false);
        }
    }

    private void sendHeartbeat(Connection connection) {
        synchronized (connection) {
            if (connection.closed.get()) {
                return;
            }
            if (!catchUpFromDatabase(connection)) {
                return;
            }
            try {
                connection.emitter.send(SseEmitter.event().comment(" keepalive"));
            } catch (IOException | IllegalStateException exception) {
                close(connection, exception);
            }
        }
    }

    private boolean catchUpFromDatabase(Connection connection) {
        AgentRunEntity run = runRepository.findById(connection.internalRunId).orElse(null);
        if (run == null) {
            close(connection, null);
            return false;
        }
        if (run.getLastEventSeq() > connection.lastSentSequence) {
            AgentEventEntity tail = eventRepository
                    .findByRunIdAndSeq(run.getId(), run.getLastEventSeq())
                    .orElse(null);
            if (tail != null) {
                sendEventInSequence(connection, tail);
            }
        }
        if (run.getStatus() != AgentRunStatus.RUNNING) {
            close(connection, null);
            return false;
        }
        return !connection.closed.get();
    }

    private void register(Connection connection) {
        Deque<Connection> connections = connectionsByUser.computeIfAbsent(
                connection.userId, ignored -> new ArrayDeque<>());
        List<Connection> evicted = new ArrayList<>();
        synchronized (connections) {
            connections.addLast(connection);
            connectionsByRun.computeIfAbsent(
                    connection.internalRunId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(connection);
            while (connections.size() > maxConnectionsPerUser) {
                evicted.add(connections.removeFirst());
            }
        }
        evicted.forEach(oldest -> close(oldest, null));
    }

    private void registerCallbacks(Connection connection) {
        connection.emitter.onCompletion(() -> close(connection, null));
        connection.emitter.onTimeout(() -> close(connection, null));
        connection.emitter.onError(error -> close(connection, error));
    }

    private void installHeartbeat(Connection connection, ScheduledFuture<?> heartbeat) {
        if (connection.closed.get()
                || !connection.heartbeat.compareAndSet(null, heartbeat)) {
            heartbeat.cancel(false);
            return;
        }
        if (connection.closed.get()) {
            ScheduledFuture<?> installed = connection.heartbeat.getAndSet(null);
            if (installed != null) {
                installed.cancel(false);
            }
        }
    }

    private void close(Connection connection, Throwable error) {
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> heartbeat = connection.heartbeat.getAndSet(null);
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        Deque<Connection> connections = connectionsByUser.get(connection.userId);
        if (connections != null) {
            synchronized (connections) {
                connections.remove(connection);
                if (connections.isEmpty()) {
                    connectionsByUser.remove(connection.userId, connections);
                }
            }
        }
        Set<Connection> runConnections = connectionsByRun.get(connection.internalRunId);
        if (runConnections != null) {
            runConnections.remove(connection);
            if (runConnections.isEmpty()) {
                connectionsByRun.remove(connection.internalRunId, runConnections);
            }
        }
        try {
            if (error == null) {
                connection.emitter.complete();
            } else {
                connection.emitter.completeWithError(error);
            }
        } catch (IllegalStateException ignored) {
            // Completion callbacks may race with a failed send; cleanup above is idempotent.
        }
    }

    private List<Connection> snapshotConnections() {
        List<Connection> snapshot = new ArrayList<>();
        for (Deque<Connection> connections : connectionsByUser.values()) {
            synchronized (connections) {
                snapshot.addAll(connections);
            }
        }
        return snapshot;
    }

    private List<Connection> snapshotRunConnections(Long internalRunId) {
        Set<Connection> connections = connectionsByRun.get(internalRunId);
        return connections == null ? List.of() : List.copyOf(connections);
    }

    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        try {
            long sequence = Long.parseLong(lastEventId);
            if (sequence < 0) throw new NumberFormatException();
            return sequence;
        } catch (NumberFormatException exception) {
            // 브라우저가 보낸 헤더가 잘못된 것이므로 클라이언트 오류다.
            // AGENT_RESPONSE_INVALID(502)는 "FastAPI 응답이 규격 밖"이라는 뜻이라 방향이 반대다.
            //
            // 0으로 보고 전체 재생하지 않는 이유 — FE 버그를 숨기게 된다. 계약이 숫자 시퀀스이므로
            // 어긋난 값은 분명히 끊는 편이 낫다.
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    @PreDestroy
    public void shutdown() {
        snapshotConnections().forEach(connection -> close(connection, null));
        heartbeatScheduler.shutdownNow();
        heartbeatSender.shutdownNow();
        eventSender.shutdownNow();
    }

    private static final class Connection {
        private final Long userId;
        private final Long internalRunId;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean heartbeatInFlight = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        private final Object queueLock = new Object();
        private final Deque<AgentEventEntity> pendingEvents = new ArrayDeque<>();
        private boolean deliveryScheduled;
        private boolean closeAfterDrain;
        private long lastSentSequence;

        private Connection(Long userId, Long internalRunId, long lastSentSequence,
                           SseEmitter emitter) {
            this.userId = userId;
            this.internalRunId = internalRunId;
            this.lastSentSequence = lastSentSequence;
            this.emitter = emitter;
        }
    }
}
