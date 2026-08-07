package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentProjectSummaryRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentProjectSummaryResult;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
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
 * 프로젝트 탭 AI 요약 생성 전용 FastAPI 클라이언트.
 *
 * <p>{@link AgentServerClient}와 분리한 이유는 계약이 다르기 때문입니다. 이쪽은 Run이 아니라
 * 단발 호출이라 SSE도, 체크포인트도, 도구 호출도 없습니다. 그냥 JSON을 보내고 JSON을 받습니다.
 * 스트리밍 클라이언트에 메서드를 하나 더 붙이면 그 클래스의 이벤트 타임아웃·세그먼트 감시가
 * 이 호출에는 전혀 맞지 않는 채로 딸려옵니다.</p>
 *
 * <p>이 클래스의 책임은 "HTTP로 부르고, 실패 원인을 우리 에러코드로 번역하는 것"까지입니다.
 * 저장은 {@code ProjectSummaryService}가 합니다.</p>
 */
@Slf4j
@Component
public class AgentSummaryClient {

    private static final String PROJECT_SUMMARY_PATH = "/api/agent/project-summary";

    // 저장 컬럼(VARCHAR(20))과 같은 상한. 넘기면 INSERT 단계에서 500이 나므로 여기서 끊는다.
    private static final int MAX_SECTION_LENGTH = 20;

    // 규격은 4개 고정이지만 딱 4로 못 박지는 않는다 — 섹션이 늘어도 BE 배포 없이 받아야 한다.
    // 다만 무한정 받아 테이블을 채우는 사고는 막는다.
    private static final int MAX_SECTIONS = 10;

    private final HttpClient httpClient;
    private final URI summaryUri;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public AgentSummaryClient(
            @Value("${agent.server.url}") String baseUrl,
            @Value("${agent.server.connect-timeout}") long connectTimeoutMillis,
            @Value("${agent.server.summary-timeout-millis:60000}") long summaryTimeoutMillis,
            ObjectMapper objectMapper
    ) {
        requirePositive(connectTimeoutMillis, "agent.server.connect-timeout");
        requirePositive(summaryTimeoutMillis, "agent.server.summary-timeout-millis");

        // HTTP/1.1 고정 — 기본값 HTTP_2는 평문 http:// 에서 h2c 업그레이드를 시도하는데
        // uvicorn(h11)이 이를 못 받아 본문 파싱에 실패한다. AgentServerClient와 같은 이유다.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
        this.summaryUri = resolveRootPath(baseUrl, PROJECT_SUMMARY_PATH);
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(summaryTimeoutMillis);
    }

    /**
     * 재료를 넘겨 4개 섹션의 요약을 한 번에 받아옵니다.
     * 응답이 올 때까지 블로킹하므로 배치 스레드 또는 갱신 요청 스레드에서만 호출합니다.
     */
    public AgentProjectSummaryResult generate(AgentProjectSummaryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Long projectId = request.projectId();

        HttpRequest httpRequest = HttpRequest.newBuilder(summaryUri)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                // 타임아웃을 반드시 건다. 기본값은 사실상 무제한이라, FastAPI가 멈추면 호출 스레드가
                // 그대로 물려 톰캣 풀이 마르고 관계없는 API까지 같이 느려진다.
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                .build();

        HttpResponse<String> response = send(httpRequest, projectId);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[프로젝트 요약] FastAPI 오류 응답. status={} projectId={}",
                    response.statusCode(), projectId);
            throw BaseException.type(response.statusCode() >= 500
                    ? AgentErrorCode.AGENT_SERVER_UNAVAILABLE
                    : AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        return parse(response.body(), projectId);
    }

    private HttpResponse<String> send(HttpRequest request, Long projectId) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timeout) {
            log.warn("[프로젝트 요약] 응답 시간 초과. projectId={}", projectId);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
        } catch (IOException exception) {
            log.error("[프로젝트 요약] FastAPI에 연결하지 못했습니다. projectId={}", projectId, exception);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("[프로젝트 요약] 호출 스레드가 중단되었습니다. projectId={}", projectId);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        }
    }

    // 본문을 필드 단위로 역직렬화하지 않는다. BE가 읽는 값은 section 하나뿐이고,
    // 나머지는 프론트로 그대로 흘려보낼 원문이라 JsonNode인 채로 둔다.
    private AgentProjectSummaryResult parse(String body, Long projectId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException malformed) {
            log.error("[프로젝트 요약] 응답이 JSON이 아닙니다. projectId={}", projectId, malformed);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        JsonNode summaries = root.path("summaries");
        if (!summaries.isArray() || summaries.isEmpty()) {
            log.error("[프로젝트 요약] summaries 배열이 비어 있습니다. projectId={}", projectId);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        if (summaries.size() > MAX_SECTIONS) {
            log.error("[프로젝트 요약] 섹션이 너무 많습니다. count={} projectId={}",
                    summaries.size(), projectId);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        List<AgentProjectSummaryResult.Summary> parsed = new ArrayList<>(summaries.size());
        for (JsonNode summary : summaries) {
            JsonNode section = summary.path("section");
            if (!section.isTextual() || section.textValue().isBlank()
                    || section.textValue().trim().length() > MAX_SECTION_LENGTH) {
                log.error("[프로젝트 요약] section이 유효하지 않습니다. projectId={}", projectId);
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            try {
                parsed.add(new AgentProjectSummaryResult.Summary(section.textValue(), summary));
            } catch (IllegalArgumentException invalid) {
                log.error("[프로젝트 요약] 요약 항목이 객체가 아닙니다. projectId={}", projectId);
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
        }

        // 응답의 projectId는 에코 값이라 신뢰하지 않는다. 저장 키는 우리가 요청에 쓴 값이다 —
        // FastAPI가 엉뚱한 id를 돌려줘도 남의 프로젝트 배너를 덮어쓰면 안 된다.
        return new AgentProjectSummaryResult(projectId, parsed);
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
