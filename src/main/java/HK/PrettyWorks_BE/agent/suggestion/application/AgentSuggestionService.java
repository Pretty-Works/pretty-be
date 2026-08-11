package HK.PrettyWorks_BE.agent.suggestion.application;

import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionRequest;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResponse;
import HK.PrettyWorks_BE.agent.suggestion.gateway.AgentSuggestionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 에이전트 패널 추천 문구 — 재료를 모아 FastAPI에 넘기고 결과를 그대로 돌려줍니다.
 *
 * <p>BE의 역할은 게이트입니다. 무엇을 추천할지도, 어떤 문장으로 쓸지도 BE가 정하지 않습니다.
 * 토큰의 사용자로 재료를 모으고, 돌아온 칩을 해석하지 않고 프론트에 흘려보냅니다.</p>
 *
 * <p><b>이 API는 실패하지 않습니다.</b> 추천은 패널의 부가 기능이라, 에이전트 서버가 죽었다고
 * 패널 전체가 5xx로 막히면 안 됩니다. 무엇이 실패하든 200에 빈 배열을 돌려주고 프론트는
 * 추천 영역만 접습니다(규격 §6).</p>
 *
 * <p>LLM 호출을 아끼는 장치가 두 겹입니다 — 짧은 TTL 캐시({@link AgentSuggestionCache})가
 * <b>순차</b> 연타를, 아래 {@code inFlight}가 <b>동시</b> 연타를 막습니다. 캐시만으로는
 * 캐시가 비어 있는 순간 동시에 들어온 요청을 못 막고, 그때가 정확히 LLM이 여러 번 도는 순간입니다.</p>
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않은 것은 의도입니다. FastAPI 호출은 수 초가 걸리므로
 * 트랜잭션 밖에서 해야 합니다.</p>
 */
@Slf4j
@Service
public class AgentSuggestionService {

    // 서버 기준 오늘. 사용자·데이터가 전부 국내라 고정한다(MeetingDraftService와 같은 기준).
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final String DEFAULT_SCREEN = "HOME";

    // 화면 이름을 열거형으로 못 박지 않는다 — BE는 값의 의미를 모르고 그대로 넘기기만 하므로,
    // 프론트에 화면이 하나 늘어도 BE 배포가 필요 없어야 한다. 다만 길이는 끊는다.
    private static final int MAX_SCREEN_LENGTH = 20;

    // 기다리는 쪽이 이긴 쪽보다 이만큼 더 기다린다. 이긴 쪽의 총 소요는 FastAPI 응답 시간에
    // 재료 수집(DB 조회 몇 건)이 더해진 값이라, 그 몫만큼 여유를 둬야 멀쩡한 결과를 눈앞에서 놓치지 않는다.
    private static final long IN_FLIGHT_WAIT_MARGIN_MILLIS = 5_000;

    private final AgentSuggestionMaterialService materialService;
    private final AgentSuggestionClient suggestionClient;
    private final AgentSuggestionCache cache;
    private final long inFlightWaitMillis;

    /**
     * 같은 사용자·화면에 대해 지금 만들고 있는 작업. 뒤늦게 온 요청은 새로 만들지 않고 여기에 붙습니다.
     *
     * <p>탭을 두 개 여는 것만으로도 LLM 호출이 두 번 나가는데, 결과는 어차피 같습니다.
     * 값을 {@code Boolean}이 아니라 {@link CompletableFuture}로 든 이유가 여기 있습니다 —
     * "이미 만드는 중"만 알면 뒤에 온 요청에게 줄 것이 없어 빈 패널을 보여주게 됩니다.
     * 이긴 쪽의 결과를 같이 받아 가면 두 탭 모두 같은 칩을 봅니다.</p>
     *
     * <p>인스턴스 메모리라 다중 인스턴스에서는 인스턴스당 한 건까지 허용됩니다. 여기서 Redis 락까지
     * 끌어오지 않는 이유는 최악의 경우가 "LLM을 인스턴스 수만큼 부른다"이지 데이터 손상이 아니고,
     * 그마저도 먼저 끝난 쪽이 캐시에 넣으면 다음 요청부터는 사라지기 때문입니다
     * ({@code ProjectSummaryService}의 {@code generating}과 같은 판단).</p>
     */
    private final Map<String, CompletableFuture<List<JsonNode>>> inFlight = new ConcurrentHashMap<>();

    public AgentSuggestionService(
            AgentSuggestionMaterialService materialService,
            AgentSuggestionClient suggestionClient,
            AgentSuggestionCache cache,
            @Value("${agent.server.suggestion-timeout-millis:15000}") long suggestionTimeoutMillis
    ) {
        this.materialService = materialService;
        this.suggestionClient = suggestionClient;
        this.cache = cache;
        this.inFlightWaitMillis = suggestionTimeoutMillis + IN_FLIGHT_WAIT_MARGIN_MILLIS;
    }

    /**
     * 이 사용자의 지금 상황에 맞는 추천 칩을 돌려줍니다.
     *
     * <p>보관된 칩이 있으면 그대로 줍니다 — LLM 호출 없이 즉시 응답합니다. 없을 때만 만들고,
     * 그마저도 같은 사용자·화면으로 이미 만들고 있으면 그 결과를 같이 받습니다.</p>
     *
     * @param userId 토큰의 사용자. 재료를 "누구의 자격으로" 읽을지가 이 값으로 정해집니다.
     * @param screen 패널을 연 화면. 비우면 {@code HOME}.
     */
    public AgentSuggestionResponse get(Long userId, String screen) {
        String normalizedScreen = normalize(screen);

        Optional<List<JsonNode>> cached = cache.find(userId, normalizedScreen);
        if (cached.isPresent()) {
            return new AgentSuggestionResponse(cached.get());
        }

        String key = userId + ":" + normalizedScreen;
        CompletableFuture<List<JsonNode>> mine = new CompletableFuture<>();
        CompletableFuture<List<JsonNode>> winner = inFlight.putIfAbsent(key, mine);
        if (winner != null) {
            // 진 쪽. 새로 만들지 않고 이긴 쪽이 끝내기를 기다린다.
            log.debug("[에이전트 추천] 이미 만들고 있어 결과를 함께 기다립니다. userId={}", userId);
            return new AgentSuggestionResponse(await(winner, userId));
        }

        try {
            return new AgentSuggestionResponse(generate(userId, normalizedScreen, mine));
        } finally {
            inFlight.remove(key, mine);
            // 어떤 경로로 빠져나갔든 기다리는 쪽을 반드시 풀어 준다. 이미 완료된 future면 무시된다.
            // 이 한 줄이 없으면 이긴 쪽이 Error로 죽었을 때 진 쪽이 타임아웃까지 스레드를 물고 있는다.
            mine.complete(List.of());
        }
    }

    // ================================= 내부 =================================

    // 재료 수집까지 try 안에 둔다. 재료 하나하나의 실패는 MaterialService가 이미 삼키지만,
    // 그 바깥에서 예기치 못하게 터지는 경우(매핑 버그 등)까지 여기서 받아야
    // "이 API는 실패하지 않는다"가 성립한다.
    private List<JsonNode> generate(Long userId, String screen,
                                    CompletableFuture<List<JsonNode>> mine) {
        try {
            LocalDate today = LocalDate.now(SERVICE_ZONE);
            AgentSuggestionRequest materials = materialService.collect(userId, screen, today);

            List<JsonNode> suggestions = suggestionClient.generate(materials, userId).suggestions();
            // 만들어진 것만 보관한다. 아래 폴백(빈 배열)은 저장하지 않는다 — FastAPI가 잠깐
            // 흔들린 대가로 그 사용자가 TTL 내내 추천을 못 보게 되면 안 된다.
            cache.put(userId, screen, suggestions);
            mine.complete(suggestions);
            return suggestions;
        } catch (RuntimeException failure) {
            // 여기서 끊지 않는다. 추천이 없는 패널은 멀쩡하지만, 추천 때문에 안 열리는 패널은 못 쓴다.
            log.warn("[에이전트 추천] 생성에 실패해 추천 없이 내려보냅니다. userId={}", userId, failure);
            return List.of();
        }
    }

    /**
     * 이긴 쪽의 결과를 기다립니다. 못 기다리면 빈 배열입니다 — 여기서도 예외를 내보내지 않습니다.
     *
     * <p>무한정 기다리지 않는 것이 핵심입니다. 이긴 쪽이 멈추면 기다리는 요청들이 톰캣 스레드를
     * 그대로 물고 있어, 추천 하나 때문에 관계없는 API까지 느려집니다.</p>
     */
    private List<JsonNode> await(CompletableFuture<List<JsonNode>> winner, Long userId) {
        try {
            return winner.get(inFlightWaitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            log.warn("[에이전트 추천] 먼저 시작된 생성이 제한 시간 안에 끝나지 않았습니다. userId={}", userId);
            return List.of();
        } catch (ExecutionException failure) {
            // 이긴 쪽은 값으로만 완료하므로 정상적으로는 오지 않는다. 와도 패널은 열려야 한다.
            log.warn("[에이전트 추천] 먼저 시작된 생성이 실패했습니다. userId={}", userId, failure);
            return List.of();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("[에이전트 추천] 대기 중 스레드가 중단되었습니다. userId={}", userId);
            return List.of();
        }
    }

    private static String normalize(String screen) {
        if (!StringUtils.hasText(screen)) {
            return DEFAULT_SCREEN;
        }
        String trimmed = screen.trim().toUpperCase();
        return trimmed.length() <= MAX_SCREEN_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_SCREEN_LENGTH);
    }
}
