package HK.PrettyWorks_BE.agent.suggestion.gateway;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이 클라이언트가 지켜야 하는 것은 하나입니다 — <b>화면에 걸 수 없는 칩을 프론트에 넘기지 않는 것</b>.
 * {@code prompt}가 빈 칩이 통과하면 사용자가 눌렀을 때 빈 goal이 전송돼 에이전트가 400을 돌려줍니다.
 */
class AgentSuggestionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AgentSuggestionClient client;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 재료를_바디에_실어_보내고_칩을_그대로_돌려준다() throws Exception {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"suggestions":[
                      {"text":"'환율 연동 검증' 마감이 지났습니다. 처리해드릴까요?",
                       "prompt":"'환율 연동 검증' 마감이 지난 할 일을 처리해줘",
                       "kind":"overdue_task"}
                    ]}
                    """);
        });

        List<JsonNode> suggestions = client.generate(request(), 7L).suggestions();

        assertThat(captured.get().get("today").textValue()).isEqualTo("2026-08-11");
        assertThat(captured.get().get("screen").textValue()).isEqualTo("HOME");
        assertThat(suggestions).hasSize(1);
        // kind까지 원문 그대로 흘려보낸다 — BE가 스키마를 복사해 두지 않는다는 것이 이 기능의 전제다.
        assertThat(suggestions.getFirst().get("kind").textValue()).isEqualTo("overdue_task");
        assertThat(suggestions.getFirst().get("prompt").textValue())
                .isEqualTo("'환율 연동 검증' 마감이 지난 할 일을 처리해줘");
    }

    @Test
    void 빈_배열은_정상이다() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"suggestions\":[]}"));

        assertThat(client.generate(request(), 7L).suggestions()).isEmpty();
    }

    @Test
    void text나_prompt가_없는_칩은_버리고_나머지는_살린다() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"suggestions":[
                  {"text":"prompt가 없는 칩","kind":"overdue_task"},
                  {"text":"","prompt":"text가 빈 칩","kind":"due_soon"},
                  {"text":"멀쩡한 칩입니다","prompt":"이건 실행돼야 한다","kind":"leave"}
                ]}
                """));

        List<JsonNode> suggestions = client.generate(request(), 7L).suggestions();

        // 한 칩이 잘못됐다고 전체를 버리지 않는다. 추천은 부가 기능이라 하나라도 걸리는 편이 낫다.
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().get("prompt").textValue()).isEqualTo("이건 실행돼야 한다");
    }

    @Test
    void 세_개를_넘게_오면_앞에서부터_세_개만_쓴다() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"suggestions":[
                  {"text":"1","prompt":"하나"},
                  {"text":"2","prompt":"둘"},
                  {"text":"3","prompt":"셋"},
                  {"text":"4","prompt":"넷"}
                ]}
                """));

        List<JsonNode> suggestions = client.generate(request(), 7L).suggestions();

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.getLast().get("prompt").textValue()).isEqualTo("셋");
    }

    @Test
    void suggestions가_없으면_빈_목록으로_본다() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{}"));

        assertThat(client.generate(request(), 7L).suggestions()).isEmpty();
    }

    @Test
    void 에이전트_서버가_5xx면_연결_실패로_번역한다() throws Exception {
        startServer(exchange -> respond(exchange, 503, "{}"));

        assertThatThrownBy(() -> client.generate(request(), 7L))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("code", AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
    }

    @Test
    void 본문이_JSON이_아니면_응답_해석_실패로_번역한다() throws Exception {
        startServer(exchange -> respond(exchange, 200, "not json"));

        assertThatThrownBy(() -> client.generate(request(), 7L))
                .isInstanceOf(BaseException.class)
                .hasFieldOrPropertyWithValue("code", AgentErrorCode.AGENT_RESPONSE_INVALID);
    }

    // ================================= 헬퍼 =================================

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agent/suggestions", handler);
        server.start();

        client = new AgentSuggestionClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                1_000, 5_000, objectMapper);
    }

    private AgentSuggestionRequest request() {
        return AgentSuggestionRequest.builder()
                .today(LocalDate.of(2026, 8, 11))
                .screen("HOME")
                .projects(List.of())
                .tasks(List.of())
                .meetings(List.of())
                .upcomingMeetings(List.of())
                .recentQuestions(List.of())
                .build();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
