package HK.PrettyWorks_BE.agent.shared.attachment;

import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentRunRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 방금 붙인 첨부를 그 대화에서 잠깐 더 쓸 수 있게 들고 있습니다.
 *
 * <p>없으면 이런 일이 납니다 — 파일을 올리며 "이걸로 회의록 써줘"라고 하면 에이전트가
 * "어느 프로젝트인가요?"를 되묻고, 사용자가 프로젝트 이름만 답한 그 턴에는 첨부가 없어
 * "파일을 첨부해 주세요"로 되돌아갑니다. 사용자는 같은 파일을 다시 올려야 합니다.</p>
 *
 * <p><b>매 턴 다시 실어 보내는 것과는 다릅니다.</b> 대화가 길어질수록 같은 파일이 프롬프트에
 * 계속 실리는 것을 막으려고 원래 첨부는 그 턴에서 끝내는 설계였고, 그 판단은 그대로 둡니다.
 * 여기서 여는 것은 "되묻고 답하는 몇 분"뿐이라 TTL이 짧습니다.</p>
 *
 * <p>저장소가 Redis인 이유와 실패를 삼키는 이유는 {@code AgentSuggestionCache}와 같습니다 —
 * 보관 기간이 짧아 TTL 만료가 곧 정리이고, 최악의 경우가 "사용자가 파일을 다시 올린다"이지
 * 실행이 죽는 것이 아닙니다.</p>
 */
@Slf4j
@Component
public class AgentAttachmentCarryCache {

    private static final String KEY_PREFIX = "agent:attachments:";

    private static final TypeReference<List<AgentRunRequest.AttachedFile>> FILE_LIST =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AgentAttachmentCarryCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${agent.upload.carry-ttl-minutes:10}") long carryTtlMinutes
    ) {
        if (carryTtlMinutes < 0) {
            throw new IllegalStateException("agent.upload.carry-ttl-minutes must not be negative");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(carryTtlMinutes);
    }

    /**
     * 이 대화에서 직전에 올린 첨부. 없거나 시간이 지났으면 빈 목록입니다.
     */
    public List<AgentRunRequest.AttachedFile> find(Long conversationId) {
        if (disabled() || conversationId == null) {
            return List.of();
        }
        try {
            String cached = redisTemplate.opsForValue().get(key(conversationId));
            return cached == null ? List.of() : objectMapper.readValue(cached, FILE_LIST);
        } catch (RuntimeException failure) {
            // 저장 형식을 바꿨는데 옛 값이 남은 경우, Redis 장애 등. 첨부 없이 진행하면 된다.
            log.warn("[에이전트 첨부] 직전 첨부를 읽지 못했습니다. conversationId={}",
                    conversationId, failure);
            return List.of();
        }
    }

    /**
     * 이번 턴에 올린 첨부를 보관합니다. 새로 올리면 이전 것은 덮어씁니다 —
     * 사용자가 마지막에 올린 파일이 지금 이야기하고 있는 파일입니다.
     */
    public void put(Long conversationId, List<AgentRunRequest.AttachedFile> attachments) {
        if (disabled() || conversationId == null || attachments.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(conversationId),
                    objectMapper.writeValueAsString(attachments), ttl);
        } catch (RuntimeException failure) {
            // 보관 실패의 최악은 되물었을 때 파일을 다시 올려야 하는 것이다. 실행을 깨뜨리지 않는다.
            log.warn("[에이전트 첨부] 직전 첨부를 보관하지 못했습니다. conversationId={}",
                    conversationId, failure);
        }
    }

    // 0분이면 끈다. 첨부를 이어 주지 않던 예전 동작으로 되돌리는 스위치다.
    private boolean disabled() {
        return ttl.isZero();
    }

    private String key(Long conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
