package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.constant.AgentDecision;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServerClientStreamingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AgentServerClient client;

    @AfterEach
    void tearDown() {
        if (client != null) client.shutdown();
        if (server != null) server.stop(0);
    }

    @Test
    void postsRunContractAndConsumesEventStream() throws Exception {
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        startServer(exchange -> {
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "text/event-stream; charset=UTF-8", """
                    event: step
                    data: {"text":"처리 중입니다"}

                    event: done
                    data: {"answer":"완료했습니다.","action":null}

                    """);
        });

        AgentSegmentOutcome outcome = client.startRun(request(), ignored -> {});

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.COMPLETED);
        assertThat(capturedRequest.get().get("runId").textValue()).isEqualTo("run_public_1");
        assertThat(capturedRequest.get().has("userId")).isFalse();
        assertThat(capturedRequest.get().has("conversationId")).isFalse();
    }

    @Test
    void rejectsNonEventStreamResponseWithoutReadingItAsJson() throws Exception {
        startServer(exchange -> respond(
                exchange, 200, "application/json", "{\"answer\":\"legacy\"}"));

        assertThatThrownBy(() -> client.startRun(request(), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.AGENT_RESPONSE_INVALID));
    }

    @Test
    void mapsFastApi5xxToServerUnavailable() throws Exception {
        startServer(exchange -> respond(
                exchange, 503, "application/json", "{\"detail\":\"maintenance\"}"));

        assertThatThrownBy(() -> client.startRun(request(), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.AGENT_SERVER_UNAVAILABLE));
    }

    @Test
    void mapsEofBeforeTerminalEventToInterruptedStream() throws Exception {
        startServer(exchange -> respond(exchange, 200, "text/event-stream", """
                event: step
                data: {"text":"처리 중입니다"}

                """));

        assertThatThrownBy(() -> client.startRun(request(), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.STREAM_INTERRUPTED));
    }

    @Test
    void postsResumeContractToRunCheckpoint() throws Exception {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        startServer(exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "text/event-stream", """
                    event: done
                    data: {"answer":"완료했습니다.","action":null}

                    """);
        });

        AgentResumeRequest request = AgentResumeRequest.approval(
                41L, AgentDecision.APPROVED, null, null, "secret-token", "{\"taskId\":7}");
        AgentSegmentOutcome outcome = client.resumeRun(
                "run_public_1", request, ignored -> {});

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.COMPLETED);
        assertThat(capturedPath.get())
                .isEqualTo("/api/agent/runs/run_public_1/resume");
        assertThat(capturedRequest.get().get("kind").textValue()).isEqualTo("APPROVAL");
        assertThat(capturedRequest.get().get("approvalToken").textValue())
                .isEqualTo("secret-token");
        assertThat(capturedRequest.get().get("paramsCanonical").textValue())
                .isEqualTo("{\"taskId\":7}");
    }

    @Test
    void mapsMissingResumeCheckpointToCheckpointLost() throws Exception {
        startServer(exchange -> respond(exchange, 404, "application/json", "{}"));
        AgentResumeRequest request = AgentResumeRequest.question(
                51L, objectMapper.readTree("{\"freeText\":\"회의실 A\"}"));

        assertThatThrownBy(() -> client.resumeRun("run_public_1", request, ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.CHECKPOINT_LOST));
    }

    @Test
    void timesOutWhenHeadersArriveButNoValidEventFollows() throws Exception {
        startServer(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.flush();
                try {
                    Thread.sleep(1_500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                exchange.close();
            }
        }, 500, 5_000);

        assertThatThrownBy(() -> client.startRun(request(), ignored -> {}))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(AgentErrorCode.AGENT_SERVER_TIMEOUT));
    }

    @Test
    void cancelClosesAnActiveFastApiResponseBody() throws Exception {
        CountDownLatch headersSent = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        startServer(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            headersSent.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }, 5_000, 10_000);
        CompletableFuture<Throwable> result = CompletableFuture.supplyAsync(() -> {
            try {
                client.startRun(request(), ignored -> {});
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });

        assertThat(headersSent.await(1, TimeUnit.SECONDS)).isTrue();
        try {
            client.cancelRun("run_public_1");
            assertThat(result.get(2, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(BaseException.class, exception ->
                            assertThat(exception.getCode())
                                    .isEqualTo(AgentErrorCode.STREAM_INTERRUPTED));
        } finally {
            releaseServer.countDown();
        }
    }

    private void startServer(HttpHandler handler) throws IOException {
        startServer(handler, 1_000, 5_000);
    }

    private void startServer(HttpHandler handler, long eventTimeoutMillis,
                             long segmentTimeoutMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agent/runs", handler);
        server.start();

        AgentServerEventDecoder decoder = new AgentServerEventDecoder(objectMapper);
        AgentServerSseParser parser = new AgentServerSseParser(decoder);
        client = new AgentServerClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1_000, eventTimeoutMillis, segmentTimeoutMillis, parser, objectMapper);
    }

    private AgentRunRequest request() {
        return new AgentRunRequest(
                "run_public_1",
                1L,
                "이번 주 할 일을 확인해줘",
                List.of(),
                objectMapper.readTree("{\"screen\":\"TASK_LIST\"}"),
                "WEB",
                "ko-KR"
        );
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
