package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.client.dto.AgentServerEventType;
import HK.PrettyWorks_BE.agent.client.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

// 에이전트 서버(FastAPI) 호출 전용 클라이언트.
//
// 이 클래스의 책임은 "HTTP로 부르고, 실패 원인을 우리 에러코드로 번역하는 것"까지다.
// Run 저장과 상태 전이는 AgentRunEventService가 담당한다.
@Slf4j
@Component
public class AgentServerClient {

    private static final String RUNS_PATH = "/api/agent/runs";

    private final HttpClient streamingHttpClient;
    private final URI runsUri;
    private final AgentServerSseParser sseParser;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService timeoutScheduler;
    private final long eventTimeoutMillis;
    private final long segmentTimeoutMillis;
    private final ConcurrentMap<String, Set<ActiveSegment>> activeSegments =
            new ConcurrentHashMap<>();
    private final Set<String> canceledRuns = ConcurrentHashMap.newKeySet();

    public AgentServerClient(
            @Value("${agent.server.url}") String baseUrl,
            @Value("${agent.server.connect-timeout}") long connectTimeoutMillis,
            @Value("${agent.server.event-timeout-millis:90000}") long eventTimeoutMillis,
            @Value("${agent.server.segment-timeout-millis:600000}") long segmentTimeoutMillis,
            AgentServerSseParser sseParser,
            ObjectMapper objectMapper
    ) {
        requirePositive(connectTimeoutMillis, "agent.server.connect-timeout");
        requirePositive(eventTimeoutMillis, "agent.server.event-timeout-millis");
        requirePositive(segmentTimeoutMillis, "agent.server.segment-timeout-millis");
        if (segmentTimeoutMillis <= eventTimeoutMillis) {
            throw new IllegalStateException(
                    "agent.server.segment-timeout-millis must exceed event-timeout-millis");
        }

        // 타임아웃을 반드시 건다. 기본값은 사실상 무제한이라, FastAPI가 멈추면 우리 요청 스레드가
        // 그대로 물려 톰캣 풀이 마르고 관계없는 API까지 같이 느려진다.
        //
        // HTTP/1.1로 고정한다. HttpClient 기본값은 HTTP_2라 평문 http:// 로 부를 때
        // h2c 업그레이드(Connection: Upgrade, HTTP2-Settings)를 먼저 시도하는데,
        // FastAPI를 띄우는 uvicorn(h11)은 이를 못 받아 "Unsupported upgrade request"를 남기고
        // 본문 파싱에 실패해 422를 돌려준다. 그러면 BE는 AGENT_007로 번역해 실행이 통째로 죽는다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();

        this.streamingHttpClient = httpClient;
        this.runsUri = resolveRootPath(baseUrl, RUNS_PATH);
        this.sseParser = sseParser;
        this.objectMapper = objectMapper;
        this.eventTimeoutMillis = eventTimeoutMillis;
        this.segmentTimeoutMillis = segmentTimeoutMillis;
        this.timeoutScheduler = Executors.newScheduledThreadPool(
                2, daemonFactory("agent-fastapi-timeout"));
    }

    /**
     * FastAPI에 새 v2 Run 세그먼트를 열고, 유효한 이벤트를 도착 순서대로 전달합니다.
     * 이 메서드는 세그먼트가 끝날 때까지 블로킹하므로 HTTP 요청 스레드에서 직접 호출하지 않습니다.
     */
    public AgentSegmentOutcome startRun(
            AgentRunRequest request,
            Consumer<DecodedAgentServerEvent> eventConsumer
    ) {
        Objects.requireNonNull(request, "request must not be null");
        return postSegment(runsUri, request, request.runId(), false, eventConsumer);
    }

    public AgentSegmentOutcome resumeRun(
            String runId,
            AgentResumeRequest request,
            Consumer<DecodedAgentServerEvent> eventConsumer
    ) {
        Objects.requireNonNull(request, "request must not be null");
        if (runId == null || !runId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("runId must be a safe path segment");
        }
        URI resumeUri = URI.create(runsUri.toASCIIString() + "/" + runId + "/resume");
        return postSegment(resumeUri, request, runId, true, eventConsumer);
    }

    public void cancelRun(String runId) {
        canceledRuns.add(runId);
        Set<ActiveSegment> segments = activeSegments.get(runId);
        if (segments != null) {
            List.copyOf(segments).forEach(ActiveSegment::cancel);
        }
    }

    private AgentSegmentOutcome postSegment(
            URI uri,
            Object request,
            String runId,
            boolean resume,
            Consumer<DecodedAgentServerEvent> eventConsumer
    ) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        ActiveSegment active = new ActiveSegment();
        activeSegments.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet())
                .add(active);
        try {
            if (canceledRuns.remove(runId)) {
                throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
            }
            byte[] requestBody = objectMapper.writeValueAsBytes(request);
            long requestStartedAtNanos = System.nanoTime();
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            HttpResponse<InputStream> response = sendForHeaders(httpRequest, runId, active);
            active.installBody(response.body());
            if (active.isCanceled()) {
                throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                log.error("[에이전트 SSE] FastAPI 오류 응답. status={} runId={}",
                        response.statusCode(), runId);
                if (resume && response.statusCode() == 404) {
                    throw BaseException.type(AgentErrorCode.CHECKPOINT_LOST);
                }
                throw BaseException.type(response.statusCode() >= 500
                        ? AgentErrorCode.AGENT_SERVER_UNAVAILABLE
                        : AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            requireEventStream(response, runId);
            return consumeBody(response.body(), runId, requestStartedAtNanos, eventConsumer);
        } finally {
            removeActiveSegment(runId, active);
            canceledRuns.remove(runId);
            active.close();
        }
    }

    private void removeActiveSegment(String runId, ActiveSegment active) {
        Set<ActiveSegment> segments = activeSegments.get(runId);
        if (segments != null) {
            segments.remove(active);
            if (segments.isEmpty()) {
                activeSegments.remove(runId, segments);
            }
        }
    }

    private HttpResponse<InputStream> sendForHeaders(HttpRequest request, String runId,
                                                     ActiveSegment active) {
        CompletableFuture<HttpResponse<InputStream>> responseFuture = streamingHttpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofInputStream());
        active.installFuture(responseFuture);
        try {
            // ofInputStream은 응답 헤더와 body stream을 받는 순간 future가 완료된다.
            // 이후 본문 수명은 SegmentWatchdog만 담당해 이중 timeout 경쟁을 없앤다.
            return responseFuture.get(eventTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancelAndClose(responseFuture);
            log.warn("[에이전트 SSE] 응답 헤더 시간 초과. runId={}", runId);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
        } catch (CancellationException canceled) {
            throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
        } catch (InterruptedException exception) {
            cancelAndClose(responseFuture);
            Thread.currentThread().interrupt();
            log.warn("[에이전트 SSE] 호출 스레드가 중단되었습니다. runId={}", runId);
            throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof HttpTimeoutException) {
                log.warn("[에이전트 SSE] 응답 헤더 시간 초과. runId={}", runId);
                throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
            }
            log.error("[에이전트 SSE] FastAPI에 연결하지 못했습니다. runId={}", runId, cause);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        }
    }

    private void cancelAndClose(CompletableFuture<HttpResponse<InputStream>> responseFuture) {
        if (!responseFuture.cancel(true)) {
            // timeout과 헤더 도착이 같은 순간이면 cancel이 false일 수 있다. 이 응답을 아무도
            // 소비하지 않으면 연결이 풀로 돌아가지 않으므로, 뒤늦게라도 body를 닫는다.
            responseFuture.thenAccept(response -> closeQuietly(response.body()));
        }
    }

    private AgentSegmentOutcome consumeBody(
            InputStream body,
            String runId,
            long requestStartedAtNanos,
            Consumer<DecodedAgentServerEvent> eventConsumer
    ) {
        try (SegmentWatchdog watchdog = new SegmentWatchdog(body, runId, requestStartedAtNanos)) {
            try {
                AgentSegmentOutcome outcome = sseParser.parse(
                        new InputStreamReader(body, StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)),
                        runId,
                        event -> {
                            watchdog.onEventArrived(event.event().type());
                            eventConsumer.accept(event);
                            watchdog.afterEventHandled(event.event().type());
                        });
                watchdog.throwIfExpired();
                return outcome;
            } catch (BaseException exception) {
                if (exception.getCode() == AgentErrorCode.AGENT_SERVER_TIMEOUT) {
                    throw exception;
                }
                watchdog.throwIfExpired();
                throw exception;
            } catch (IOException exception) {
                watchdog.throwIfExpired();
                if (exception instanceof CharacterCodingException) {
                    log.warn("[에이전트 SSE] UTF-8이 아닌 응답입니다. runId={}", runId);
                    throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
                }
                log.warn("[에이전트 SSE] 응답 스트림이 중단되었습니다. runId={}", runId);
                throw BaseException.type(AgentErrorCode.STREAM_INTERRUPTED);
            }
        } finally {
            // close 실패는 이미 결정된 성공·대기·실패 결과를 뒤집지 않는다.
            closeQuietly(body);
        }
    }

    private void requireEventStream(HttpResponse<InputStream> response, String runId) {
        String value = response.headers().firstValue("Content-Type").orElse(null);
        try {
            MediaType contentType = value == null ? null : MediaType.parseMediaType(value);
            if (contentType == null
                    || !MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)
                    || (contentType.getCharset() != null
                    && !StandardCharsets.UTF_8.equals(contentType.getCharset()))) {
                closeQuietly(response.body());
                log.error("[에이전트 SSE] Content-Type 불일치. contentType={} runId={}", value, runId);
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
        } catch (IllegalArgumentException invalidContentType) {
            closeQuietly(response.body());
            log.error("[에이전트 SSE] Content-Type을 해석할 수 없습니다. runId={}", runId);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    @PreDestroy
    public void shutdown() {
        activeSegments.values().forEach(segments ->
                List.copyOf(segments).forEach(ActiveSegment::cancel));
        timeoutScheduler.shutdownNow();
    }

    private static URI resolveRootPath(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl);
            if (!"http".equalsIgnoreCase(base.getScheme())
                    && !"https".equalsIgnoreCase(base.getScheme())) {
                throw new IllegalArgumentException();
            }
            return base.resolve(path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("agent.server.url must be a valid HTTP(S) URL", exception);
        }
    }

    private static void requirePositive(long value, String property) {
        if (value <= 0) {
            throw new IllegalStateException(property + " must be positive");
        }
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void closeQuietly(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
        }
    }

    private final class SegmentWatchdog implements AutoCloseable {
        private final InputStream body;
        private final String runId;
        private final AtomicReference<TimeoutKind> expired = new AtomicReference<>();
        private final ScheduledFuture<?> totalTimeout;
        private ScheduledFuture<?> eventTimeout;
        private long eventTimeoutGeneration;
        private boolean completed;

        private SegmentWatchdog(InputStream body, String runId, long requestStartedAtNanos) {
            this.body = body;
            this.runId = runId;
            try {
                long totalRemaining = remainingNanos(requestStartedAtNanos, segmentTimeoutMillis);
                if (totalRemaining == 0L) {
                    this.totalTimeout = null;
                    expire(TimeoutKind.SEGMENT);
                } else {
                    this.totalTimeout = timeoutScheduler.schedule(
                            () -> expire(TimeoutKind.SEGMENT), totalRemaining, TimeUnit.NANOSECONDS);
                }

                long eventRemaining = remainingNanos(requestStartedAtNanos, eventTimeoutMillis);
                if (eventRemaining == 0L) {
                    expire(TimeoutKind.EVENT);
                } else {
                    scheduleEventTimeout(eventRemaining);
                }
            } catch (RejectedExecutionException shuttingDown) {
                closeQuietly(body);
                throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
            }
            throwIfExpired();
        }

        synchronized void onEventArrived(AgentServerEventType type) {
            throwIfExpired();
            if (eventTimeout != null) {
                eventTimeoutGeneration++;
                eventTimeout.cancel(false);
                eventTimeout = null;
            }
            if (type != AgentServerEventType.STEP) {
                completed = true;
                if (totalTimeout != null) {
                    totalTimeout.cancel(false);
                }
            }
        }

        synchronized void afterEventHandled(AgentServerEventType type) {
            throwIfExpired();
            if (type == AgentServerEventType.STEP && !completed) {
                scheduleEventTimeout(TimeUnit.MILLISECONDS.toNanos(eventTimeoutMillis));
            }
        }

        synchronized void throwIfExpired() {
            TimeoutKind kind = expired.get();
            if (kind != null) {
                log.warn("[에이전트 SSE] {} 시간 제한 초과. runId={}", kind.logName, runId);
                throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
            }
        }

        private void scheduleEventTimeout(long delayNanos) {
            long generation = ++eventTimeoutGeneration;
            eventTimeout = timeoutScheduler.schedule(
                    () -> expireEvent(generation), delayNanos, TimeUnit.NANOSECONDS);
        }

        private synchronized void expireEvent(long generation) {
            if (generation == eventTimeoutGeneration) {
                expire(TimeoutKind.EVENT);
            }
        }

        private long remainingNanos(long startedAtNanos, long timeoutMillis) {
            long elapsed = System.nanoTime() - startedAtNanos;
            return Math.max(0L, TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - elapsed);
        }

        private synchronized void expire(TimeoutKind kind) {
            if (!completed && expired.compareAndSet(null, kind)) {
                closeQuietly(body);
            }
        }

        @Override
        public synchronized void close() {
            completed = true;
            if (totalTimeout != null) {
                totalTimeout.cancel(false);
            }
            if (eventTimeout != null) {
                eventTimeout.cancel(false);
            }
        }
    }

    private enum TimeoutKind {
        EVENT("이벤트 무소식"),
        SEGMENT("세그먼트 총");

        private final String logName;

        TimeoutKind(String logName) {
            this.logName = logName;
        }
    }

    private static final class ActiveSegment {
        private final java.util.concurrent.atomic.AtomicBoolean canceled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final AtomicReference<CompletableFuture<HttpResponse<InputStream>>> future =
                new AtomicReference<>();
        private final AtomicReference<InputStream> body = new AtomicReference<>();

        private void installFuture(
                CompletableFuture<HttpResponse<InputStream>> responseFuture) {
            future.set(responseFuture);
            if (canceled.get()) {
                responseFuture.cancel(true);
            }
        }

        private void installBody(InputStream responseBody) {
            body.set(responseBody);
            if (canceled.get()) {
                closeQuietly(responseBody);
            }
        }

        private boolean isCanceled() {
            return canceled.get();
        }

        private void cancel() {
            canceled.set(true);
            CompletableFuture<HttpResponse<InputStream>> responseFuture = future.get();
            if (responseFuture != null) {
                responseFuture.cancel(true);
            }
            closeQuietly(body.get());
        }

        private void close() {
            closeQuietly(body.getAndSet(null));
        }
    }
}
