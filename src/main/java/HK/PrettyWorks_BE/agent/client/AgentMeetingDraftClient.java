package HK.PrettyWorks_BE.agent.client;

import HK.PrettyWorks_BE.agent.client.dto.AgentMeetingDraftRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentMeetingDraftResult;
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
 * 회의록 초안 생성 전용 FastAPI 클라이언트.
 *
 * <p>{@link AgentServerClient}와 분리한 이유는 {@link AgentSummaryClient}와 같습니다. 이쪽은
 * Run이 아니라 단발 호출이라 SSE도, 체크포인트도, 도구 호출도 없습니다. 그냥 JSON을 보내고
 * JSON을 받습니다.</p>
 *
 * <p>이 클래스의 책임은 "HTTP로 부르고, 실패 원인을 우리 에러코드로 번역하는 것"까지입니다.
 * 받은 값이 쓸 수 있는 값인지(명단에 있는 참석자인지, 날짜 형식이 맞는지)는
 * {@code MeetingDraftService}가 판단합니다.</p>
 */
@Slf4j
@Component
public class AgentMeetingDraftClient {

    private static final String MEETING_DRAFT_PATH = "/api/agent/meeting-draft";

    // 참석자 후보는 프로젝트 참여자(최대 100명)뿐이라 그보다 긴 배열은 의미가 없다.
    // 어차피 뒤에서 명단과 대조해 걸러지지만, 비정상 응답이 힙을 채우는 것은 여기서 끊는다.
    private static final int MAX_ATTENDEE_IDS = 200;

    private final HttpClient httpClient;
    private final URI draftUri;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public AgentMeetingDraftClient(
            @Value("${agent.server.url}") String baseUrl,
            @Value("${agent.server.connect-timeout}") long connectTimeoutMillis,
            @Value("${agent.server.draft-timeout}") long draftTimeoutMillis,
            ObjectMapper objectMapper
    ) {
        requirePositive(connectTimeoutMillis, "agent.server.connect-timeout");
        requirePositive(draftTimeoutMillis, "agent.server.draft-timeout");

        // HTTP/1.1 고정 — 기본값 HTTP_2는 평문 http:// 에서 h2c 업그레이드를 시도하는데
        // uvicorn(h11)이 이를 못 받아 본문 파싱에 실패한다. 다른 클라이언트와 같은 이유다.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
        this.draftUri = resolveRootPath(baseUrl, MEETING_DRAFT_PATH);
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(draftTimeoutMillis);
    }

    /**
     * 녹취록을 넘겨 회의록 초안 한 벌을 받아옵니다.
     * 응답이 올 때까지 블로킹하므로 요청 스레드에서만 호출합니다.
     */
    public AgentMeetingDraftResult generate(AgentMeetingDraftRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        HttpRequest httpRequest = HttpRequest.newBuilder(draftUri)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                // 타임아웃을 반드시 건다. 기본값은 사실상 무제한이라, FastAPI가 멈추면 호출 스레드가
                // 그대로 물려 톰캣 풀이 마르고 관계없는 API까지 같이 느려진다.
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                .build();

        HttpResponse<String> response = send(httpRequest);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[회의록 초안] FastAPI 오류 응답. status={}", response.statusCode());
            throw BaseException.type(response.statusCode() >= 500
                    ? AgentErrorCode.AGENT_SERVER_UNAVAILABLE
                    : AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        return parse(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timeout) {
            log.warn("[회의록 초안] 응답 시간이 초과되었습니다. timeout={}ms", requestTimeout.toMillis());
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_TIMEOUT);
        } catch (IOException exception) {
            log.error("[회의록 초안] FastAPI에 연결하지 못했습니다.", exception);
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("[회의록 초안] 호출 스레드가 중단되었습니다.");
            throw BaseException.type(AgentErrorCode.AGENT_SERVER_UNAVAILABLE);
        }
    }

    private AgentMeetingDraftResult parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException malformed) {
            log.error("[회의록 초안] 응답이 JSON이 아닙니다.", malformed);
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        if (!root.isObject()) {
            log.error("[회의록 초안] 응답이 객체가 아닙니다.");
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        return new AgentMeetingDraftResult(
                text(root, "title"),
                text(root, "meetingDate"),
                text(root, "location"),
                text(root, "purpose"),
                text(root, "content"),
                text(root, "followUp"),
                attendeeUserIds(root));
    }

    // 근거 없는 필드는 null이 규격이다. 필드가 통째로 빠져 있어도, null이어도, 공백뿐이어도
    // 모두 "없음"으로 같게 다룬다 — 화면에서는 셋 다 빈 칸이고, 구분해 봐야 할 일이 없다.
    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue();
    }

    // 규격상 배열 고정이라 없거나 null이면 "참석자 없음"으로 본다. 다만 배열이 아닌 값이 오면
    // 계약이 깨진 것이므로 조용히 넘기지 않는다 — 그래야 LLM팀이 스키마를 바꿨을 때 바로 드러난다.
    private static List<Long> attendeeUserIds(JsonNode root) {
        JsonNode value = root.path("attendeeUserIds");
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            log.error("[회의록 초안] attendeeUserIds가 배열이 아닙니다.");
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        List<Long> ids = new ArrayList<>(Math.min(value.size(), MAX_ATTENDEE_IDS));
        for (JsonNode element : value) {
            if (ids.size() == MAX_ATTENDEE_IDS) {
                log.warn("[회의록 초안] 참석자 id가 너무 많아 앞의 {}건만 씁니다. received={}",
                        MAX_ATTENDEE_IDS, value.size());
                break;
            }
            Long id = toUserId(element);
            // 하나가 이상하다고 전체를 버리지 않는다. 명단 대조에서 어차피 걸러지고,
            // 참석자 한 명이 빠진 초안이 초안 자체가 없는 것보다 낫다.
            if (id == null) {
                log.warn("[회의록 초안] 참석자 id를 해석하지 못해 건너뜁니다.");
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    // 숫자가 정석이지만 문자열("1")도 받는다. LLM 출력에서 흔한 흔들림이고,
    // 여기서 거절하면 사용자에게는 이유를 알 수 없는 502로 보인다.
    private static Long toUserId(JsonNode element) {
        if (element.isIntegralNumber()) {
            return element.longValue();
        }
        if (element.isTextual()) {
            try {
                return Long.valueOf(element.textValue().trim());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
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
