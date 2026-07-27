package HK.PrettyWorks_BE.auth.service;

import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// 무효화된 세션(sid)을 기록해 access token을 만료 전에 차단합니다.
// access token은 stateless라 서버가 회수할 수 없으므로, "이 세션은 죽었다"는 사실을 따로 들고 있어야 합니다.
//
// 저장소로 Redis를 쓰는 이유: 보관 기간이 access 수명뿐이라 TTL 자동 만료가 그대로 정리 역할을 합니다.
// (refresh token은 세션 목록·family 조회가 필요해 DB에 둡니다)
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:session:revoked:";
    // 서버 간 시계 오차를 감안한 여유입니다.
    private static final Duration CLOCK_SKEW_MARGIN = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    // 세션을 무효화합니다. 로그아웃과 refresh token 재사용(도난) 탐지 시 호출합니다.
    public void revoke(String sessionId) {
        try {
            redisTemplate.opsForValue().set(key(sessionId), "1", retention());
        } catch (RuntimeException e) {
            // 조회 실패는 "한 번 통과"로 끝나지만, 기록 실패는 그 세션이 영영 차단되지 않는다는 뜻이라 더 위험합니다.
            log.error("[세션 무효화 기록 실패] Redis 장애로 이 세션의 access token이 만료까지 유효합니다 - sid={}",
                    sessionId, e);
        }
    }

    // 무효화된 세션인지 확인합니다. 모든 인증 요청에서 호출됩니다.
    public boolean isRevoked(String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(sessionId)));
        } catch (RuntimeException e) {
            // fail-open: 가용성을 우선해 통과시킵니다. 대신 이 로그가 쌓이면 차단이 무력화된 상태이므로 즉시 확인해야 합니다.
            log.error("[세션 무효화 조회 실패] Redis 장애로 세션 검증을 건너뜁니다 - sid={}", sessionId, e);
            return false;
        }
    }

    // 보관 기간은 access token 수명에서 파생합니다.
    // 별도 설정값으로 두면 access 수명만 늘렸을 때 차단이 먼저 풀려 탈취 토큰이 되살아납니다.
    private Duration retention() {
        return Duration.ofMillis(jwtUtil.getAccessTokenExpirePeriod()).plus(CLOCK_SKEW_MARGIN);
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
