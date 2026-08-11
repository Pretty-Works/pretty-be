package HK.PrettyWorks_BE.agent.suggestion.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 추천 칩을 사용자·화면 단위로 잠시 보관합니다.
 *
 * <p>이 캐시가 막는 것은 <b>비용</b>입니다. 캐시가 없으면 패널을 열 때마다, 연타하면 연타한 만큼
 * LLM이 돕니다. 반대로 오래 들고 있을 값도 아닙니다 — 할 일 하나만 체크해도 추천이 낡습니다.
 * 그래서 짧은 TTL만 두고 무효화는 하지 않습니다(만료가 곧 무효화입니다).</p>
 *
 * <p>저장소로 Redis를 쓰는 이유는 {@code TokenBlacklistService}와 같습니다 — 보관 기간이 짧아
 * TTL 자동 만료가 그대로 정리 역할을 하고, 인스턴스가 여러 대여도 한 벌만 만들게 됩니다.</p>
 *
 * <p><b>Redis가 죽어도 이 기능은 죽지 않습니다.</b> 읽기 실패는 캐시 미스로, 쓰기 실패는 로그로
 * 끝냅니다 — 최악의 경우가 "LLM을 좀 더 자주 부른다"이지 패널이 안 열리는 것이 아닙니다.</p>
 */
@Slf4j
@Component
public class AgentSuggestionCache {

    private static final String KEY_PREFIX = "agent:suggestions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AgentSuggestionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${agent.suggestion.cache-ttl-minutes:5}") long cacheTtlMinutes
    ) {
        if (cacheTtlMinutes < 0) {
            throw new IllegalStateException(
                    "agent.suggestion.cache-ttl-minutes must not be negative");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(cacheTtlMinutes);
    }

    /**
     * 보관된 칩을 찾습니다. 없거나 Redis가 응답하지 않으면 비어 있는 값입니다(= 새로 만들면 됨).
     *
     * <p>빈 목록도 유효한 캐시 값입니다. "추천할 것이 없다"는 것도 LLM이 내린 결론이라,
     * 그때마다 다시 물으면 아무것도 없는 사용자에게 가장 자주 LLM을 부르게 됩니다.</p>
     */
    public Optional<List<JsonNode>> find(Long userId, String screen) {
        if (disabled()) {
            return Optional.empty();
        }
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(key(userId, screen));
        } catch (RuntimeException failure) {
            log.warn("[에이전트 추천] 캐시를 읽지 못해 새로 만듭니다. userId={}", userId, failure);
            return Optional.empty();
        }
        if (cached == null) {
            return Optional.empty();
        }

        try {
            JsonNode parsed = objectMapper.readTree(cached);
            if (!parsed.isArray()) {
                return Optional.empty();
            }
            List<JsonNode> suggestions = new ArrayList<>(parsed.size());
            parsed.forEach(suggestions::add);
            return Optional.of(List.copyOf(suggestions));
        } catch (JacksonException malformed) {
            // 저장 형식을 바꿨는데 옛 값이 남아 있는 경우 등. 버리고 새로 만들면 된다.
            log.warn("[에이전트 추천] 캐시 값을 해석하지 못해 버립니다. userId={}", userId, malformed);
            return Optional.empty();
        }
    }

    /**
     * 칩을 보관합니다.
     *
     * <p><b>호출부는 LLM이 실제로 만들어 준 결과만 넘겨야 합니다.</b> 생성에 실패해 빈 배열로
     * 폴백한 것을 여기 넣으면, FastAPI가 잠깐 흔들린 대가로 그 사용자는 TTL 내내 추천을 못 봅니다.</p>
     */
    public void put(Long userId, String screen, List<JsonNode> suggestions) {
        if (disabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue()
                    .set(key(userId, screen), objectMapper.writeValueAsString(suggestions), ttl);
        } catch (RuntimeException failure) {
            // 저장 실패는 다음 요청에서 LLM을 한 번 더 부르는 것으로 끝난다. 요청을 깨뜨리지 않는다.
            log.warn("[에이전트 추천] 캐시에 저장하지 못했습니다. userId={}", userId, failure);
        }
    }

    // 0분이면 캐시를 끈다. LLM 출력 자체를 확인해야 하는 로컬 디버깅용 스위치다.
    private boolean disabled() {
        return ttl.isZero();
    }

    // 화면까지 키에 넣는다. 같은 사용자라도 HOME과 PROJECT는 고를 후보가 달라
    // 한 키로 합치면 다른 화면의 칩이 걸린다.
    private String key(Long userId, String screen) {
        return KEY_PREFIX + userId + ":" + screen;
    }
}
