package HK.PrettyWorks_BE.agent.suggestion.gateway;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionRequest;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResult;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 에이전트 패널 추천 문구 생성 전용 FastAPI 클라이언트.
 *
 * <p>{@code AgentServerClient}와 분리한 이유는 계약이 다르기 때문입니다. 이쪽은 Run이 아니라
 * 단발 호출이라 SSE도, 체크포인트도, 도구 호출도 없습니다. 그냥 JSON을 보내고 JSON을 받습니다
 * ({@code AgentSummaryClient}와 같은 구조).</p>
 *
 * <p>이 클래스의 책임은 "HTTP로 부르고, 화면에 걸 수 없는 칩을 걸러내는 것"까지입니다.
 * 문구 자체는 손대지 않습니다.</p>
 */
@Slf4j
@Component
public class AgentSuggestionClient {

    private static final String SUGGESTIONS_PATH = "/api/agent/suggestions";

    // 규격상 0~3개. 그보다 많이 와도 패널에 걸 자리가 없으므로 앞에서부터 3개만 쓴다
    // (우선순위 순으로 정렬돼 오므로 앞이 더 중요한 후보다).
    private static final int MAX_SUGGESTIONS = 3;

    private final HttpClient httpClient;
    private final URI suggestionsUri;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public AgentSuggestionClient(
            @Value("${agent.server.url}") String baseUrl,
            @Value("${agent.server.connect-timeout}") long connectTimeoutMillis,
            @Value("${agent.server.suggestion-timeout-millis:15000}") long suggestionTimeoutMillis,
            ObjectMapper objectMapper
    ) {
        requirePositive(connectTimeoutMillis, "agent.server.connect-timeout");
        requirePositive(suggestionTimeoutMillis, "agent.server.suggestion-timeout-millis");

        // HTTP/1.1 고정 — 기본값 HTTP_2는 평문 http:// 에서 h2c 업그레이드를 시도하는데
        // uvicorn(h11)이 이를 못 받아 본문 파싱에 실패한다. AgentSummaryClient와 같은 이유다.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
        this.suggestionsUri = resolveRootPath(baseUrl, SUGGESTIONS_PATH);
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(suggestionTimeoutMillis);
    }

    /**
     * 재료를 넘겨 추천 칩 0~3개를 받아옵니다. 응답이 올 때까지 블로킹합니다.
     *
     * <p>호출부({@code AgentSuggestionService})가 모든 예외를 삼키므로 여기서는 실패를 숨기지 않고
     * 그대로 던집니다 — 어디서 무엇이 실패했는지는 로그에 남아야 합니다.</p>
     */
    public AgentSuggestionResult generate(AgentSuggestionRequest request, Long userId) {
        Objects.requireNonNull(request, "request must not be null");

        HttpRequest httpRequest = HttpRequest.newBuilder(suggestionsUri)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                // 타임아웃을 반드시 건다. 기본값은 사실상 무제한이라, FastAPI가 멈추면 호출 스레드가
                // 그대로 물려 톰캣 풀이 마르고 관계없는 API까지 같이 느려진다.
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                .build();

        HttpResponse<String> response = send(httpRequest, userId);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[에이전트 추천] FastAPI 오류 응답. status={} userId={}",
                    response.statusCode(), userId);
            throw BaseException.type(response.statusCode() >= 500
                    ? AgentErrorCode.AGENT_SERVER_UNAVAILABLE
                    : AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        return parse(response.body(), userId);
    }

    private HttpResponse<String> send(HttpRequest request, Long userId) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timeout) {
            log.warn("[에이전트 추천] 응답 시간 초과. userId={}", userId);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
        } catch (IOException exception) {
            log.error("[에이전트 추천] FastAPI에 연결하지 못했습니다. userId={}", userId, exception);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("[에이전트 추천] 호출 스레드가 중단되었습니다. userId={}", userId);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        }
    }

    /**
     * 본문을 필드 단위로 역직렬화하지 않습니다. 프론트로 그대로 흘려보낼 원문이라 JsonNode인 채로 둡니다.
     *
     * <p>다만 <b>화면에 걸 수 없는 칩은 여기서 버립니다</b>. {@code prompt}가 비어 있으면 사용자가
     * 칩을 눌렀을 때 빈 {@code goal}이 전송돼 에이전트가 400을 돌려주고, {@code text}가 비어 있으면
     * 아무 글자도 없는 칩이 걸립니다. 둘 다 사용자에게는 "눌러도 안 되는 버튼"으로 보입니다.</p>
     *
     * <p>한 칩이 잘못됐다고 전체를 버리지는 않습니다 — 추천은 부가 기능이라 두 개라도 걸리는 편이
     * 낫습니다. 대신 버린 사실은 로그로 남깁니다.</p>
     */
    private AgentSuggestionResult parse(String body, Long userId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException malformed) {
            log.error("[에이전트 추천] 응답이 JSON이 아닙니다. userId={}", userId, malformed);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        JsonNode suggestions = root.path("suggestions");
        // 빈 배열은 정상이다(재료가 없어 고를 후보가 없는 경우) — 요약과 달리 여기서 끊지 않는다.
        if (suggestions.isMissingNode() || suggestions.isNull()) {
            return new AgentSuggestionResult(List.of());
        }
        if (!suggestions.isArray()) {
            log.error("[에이전트 추천] suggestions가 배열이 아닙니다. userId={}", userId);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        List<JsonNode> usable = new ArrayList<>(MAX_SUGGESTIONS);
        for (JsonNode suggestion : suggestions) {
            if (usable.size() == MAX_SUGGESTIONS) {
                break;
            }
            if (isUsable(suggestion)) {
                usable.add(suggestion);
            }
        }

        int received = suggestions.size();
        if (usable.size() != Math.min(received, MAX_SUGGESTIONS)) {
            log.warn("[에이전트 추천] 걸 수 없는 칩을 걸렀습니다. received={} usable={} userId={}",
                    received, usable.size(), userId);
        }
        return new AgentSuggestionResult(usable);
    }

    // text와 prompt는 없으면 칩이 동작하지 않으므로 필수. kind는 아이콘 매핑용이라
    // 없어도 프론트가 기본 아이콘으로 처리한다 — 검사하지 않는다.
    private static boolean isUsable(JsonNode suggestion) {
        return suggestion.isObject()
                && hasText(suggestion.path("text"))
                && hasText(suggestion.path("prompt"));
    }

    private static boolean hasText(JsonNode node) {
        return node.isTextual() && !node.textValue().isBlank();
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
}
