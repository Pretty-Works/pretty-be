package HK.PrettyWorks_BE.agent.execution.gateway;

import HK.PrettyWorks_BE.agent.execution.domain.AgentSegmentOutcome;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentResumeRequest;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
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
        // conversationId는 FastAPI RunRequest의 필수 필드라 그대로 실어 보낸다(AgentRunRequest 주석).
        // userId와 달리 신원이 아니라서 빼지 않는다.
        assertThat(capturedRequest.get().get("conversationId").longValue()).isEqualTo(1L);
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
                41L, "call-1", AgentDecision.APPROVED, null, null,
                "secret-token", "{\"taskId\":7}", false);
        AgentSegmentOutcome outcome = client.resumeRun(
                "run_public_1", request, ignored -> {});

        assertThat(outcome).isEqualTo(AgentSegmentOutcome.COMPLETED);
        assertThat(capturedPath.get())
                .isEqualTo("/api/agent/runs/run_public_1/resume");
        assertThat(capturedRequest.get().get("kind").textValue()).isEqualTo("APPROVAL");
        assertThat(capturedRequest.get().get("toolCallId").textValue()).isEqualTo("call-1");
        assertThat(capturedRequest.get().get("decision").textValue()).isEqualTo("APPROVED");
        assertThat(capturedRequest.get().get("approvalToken").textValue())
                .isEqualTo("secret-token");
        assertThat(capturedRequest.get().get("paramsCanonical").textValue())
                .isEqualTo("{\"taskId\":7}");
    }

    // 첨부는 FastAPI RunRequest 의 attachments[{name, content}] 자리에 실려야 한다.
    // files 로 보내면 pydantic 이 통째로 버려, 파일을 올려도 에이전트가 존재조차 모른다.
    @Test
    void postsAttachmentsUnderTheNameFastApiReads() throws Exception {
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        startServer(exchange -> {
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "text/event-stream", """
                    event: done
                    data: {"answer":"완료했습니다.","action":null}

                    """);
        }, 30_000, 60_000);

        AgentRunRequest request = new AgentRunRequest(
                "run_public_1", 1L, "요약해줘", List.of(),
                objectMapper.readTree("{\"screen\":\"HOME\"}"), "WEB", "ko-KR",
                List.of(new AgentRunRequest.AttachedFile("회의록.txt", "text/plain", 12L,
                        HK.PrettyWorks_BE.agent.shared.attachment.AgentFileEncoding.TEXT,
                        "회의 내용입니다")),
                true);
        client.startRun(request, ignored -> {});

        JsonNode body = capturedRequest.get();
        assertThat(body.has("files")).isFalse();
        assertThat(body.at("/attachments/0/name").textValue()).isEqualTo("회의록.txt");
        assertThat(body.at("/attachments/0/content").textValue()).isEqualTo("회의 내용입니다");
        assertThat(body.get("autoApprove").booleanValue()).isTrue();
    }

    // 질문 재개 본문은 FastAPI ResumeRequest 필드명(questionId·selectedIds·text)이어야 한다.
    // 이름이 어긋나면 pydantic 이 값을 통째로 버려 400 → AGENT_007 로 실행이 죽는다.
    @Test
    void postsQuestionResumeWithFastApiFieldNames() throws Exception {
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        // 본문 계약만 보는 시험이라 타임아웃은 넉넉히 준다 (기본 1초는 느린 CI 에서 흔들린다).
        startServer(exchange -> {
            capturedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "text/event-stream", """
                    event: done
                    data: {"answer":"완료했습니다.","action":null}

                    """);
        }, 30_000, 60_000);

        AgentResumeRequest request = AgentResumeRequest.question(51L, objectMapper.readTree(
                "{\"selectedOptionIds\":[\"2\"],\"freeText\":\"회의실 A\"}"), false);
        client.resumeRun("run_public_1", request, ignored -> {});

        JsonNode body = capturedRequest.get();
        assertThat(body.get("questionId").longValue()).isEqualTo(51L);
        assertThat(body.at("/selectedIds/0").textValue()).isEqualTo("2");
        assertThat(body.get("text").textValue()).isEqualTo("회의실 A");
    }

    @Test
    void mapsMissingResumeCheckpointToCheckpointLost() throws Exception {
        startServer(exchange -> respond(exchange, 404, "application/json", "{}"));
        AgentResumeRequest request = AgentResumeRequest.question(
                51L, objectMapper.readTree("{\"freeText\":\"회의실 A\"}"), false);

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

        AgentServerEventDecoder decoder =
                new AgentServerEventDecoder(objectMapper, "https://agent.example.com");
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
                "ko-KR",
                List.of(),
                false
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
