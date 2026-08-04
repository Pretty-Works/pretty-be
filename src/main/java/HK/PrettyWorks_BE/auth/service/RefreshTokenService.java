package HK.PrettyWorks_BE.auth.service;

import HK.PrettyWorks_BE.auth.constant.RevokeReason;
import HK.PrettyWorks_BE.auth.domain.RefreshTokenEntity;
import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import HK.PrettyWorks_BE.auth.repository.RefreshTokenRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.Sha256;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// 로그인 세션(refresh token) 관리. 세션 생성·회전·무효화와 도난 탐지를 담당합니다.
//
// 세션 = sessionId(family) 하나. 한 세션 안에서 토큰이 회전하며 행이 쌓이고,
// 회전된 행은 지우지 않고 revoked 표시만 남겨 "폐기된 토큰의 재사용 = 도난"을 탐지합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    // refresh_tokens.user_agent 컬럼 길이와 맞춥니다.
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtUtil jwtUtil;

    @Value("${auth.session.max-per-user}")
    private int maxSessionsPerUser;

    // 로그인: 새 세션을 만들고 sessionId를 반환합니다.
    // 활성 세션이 상한을 넘으면 가장 오래된 세션부터 밀어냅니다(새 로그인은 항상 성공).
    // 회전 시 이전 행을 폐기하므로 세션 하나당 revoked_at IS NULL 인 행은 정확히 하나입니다.
    // 곧 만들 세션의 자리를 미리 비워야 하므로 상한에서 1을 뺀 값과 비교합니다.
    @Transactional
    public String createSession(Long userId) {
        List<RefreshTokenEntity> activeSessions =
                refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);

        int excess = activeSessions.size() - (maxSessionsPerUser - 1);
        for (int i = 0; i < excess; i++) {
            revokeSession(activeSessions.get(i).getSessionId(), RevokeReason.SESSION_LIMIT);
        }

        return UUID.randomUUID().toString();
    }

    // 발급된 refresh token을 세션에 기록합니다. 로그인·회전 양쪽에서 사용합니다.
    // 원문은 저장하지 않고 해시만 남깁니다.
    @Transactional
    public void save(String sessionId, Long userId, String refreshToken, String userAgent) {
        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(jwtUtil.getRefreshTokenExpirePeriod(), ChronoUnit.MILLIS);

        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .sessionId(sessionId)
                .userId(userId)
                .tokenHash(Sha256.hash(refreshToken))
                .expiresAt(expiresAt)
                .userAgent(truncateUserAgent(userAgent))
                .build());
    }

    // User-Agent는 255자를 넘는 경우가 있어 잘라서 저장합니다. 표시용 값이라 잘려도 무방하고,
    // 그대로 넣으면 길이 초과로 로그인·재발급 자체가 실패합니다.
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }

        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }

    // 재발급: 제시된 토큰을 폐기하고, 이어서 쓸 sessionId를 반환합니다.
    // 이미 폐기된 토큰이 다시 오면, 폐기 사유를 보고 도난인지 아닌지를 가릅니다.
    @Transactional
    public String rotate(String refreshToken) {
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(Sha256.hash(refreshToken))
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        if (stored.isRevoked()) {
            // 정상 회전으로 폐기된 토큰이 다시 왔다 = 누군가 옛 토큰을 들고 있다는 뜻이므로 세션을 통째로 끊습니다.
            if (stored.isReuseOfRotatedToken()) {
                log.warn("[refresh token 재사용 감지] 세션을 무효화합니다 - userId={}, sid={}",
                        stored.getUserId(), stored.getSessionId());
                revokeSession(stored.getSessionId(), RevokeReason.THEFT);
            }
            // 로그아웃 등으로 이미 끊긴 세션의 뒤늦은 요청은 도난이 아니므로 경보 없이 거부만 합니다.
            throw BaseException.type(GlobalErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        stored.revoke(now, RevokeReason.ROTATED);
        stored.markUsed(now);

        return stored.getSessionId();
    }

    // 세션 무효화: DB의 refresh token을 모두 폐기하고, 블랙리스트로 access token까지 즉시 차단합니다.
    // 로그아웃·도난 탐지·세션 수 초과·퇴사 차단에서 공통으로 사용합니다.
    //
    // 같은 클래스 안에서도 호출되므로(createSession·rotate) 그 경우엔 프록시를 타지 않습니다.
    // 지금은 호출자들이 모두 트랜잭션 안이라 결과가 같지만, 전파 속성을 REQUIRES_NEW 등으로 바꾸면
    // 내부 호출에서는 적용되지 않으니 그때는 호출 구조를 함께 바꿔야 합니다.
    @Transactional
    public void revokeSession(String sessionId, RevokeReason reason) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findBySessionId(sessionId)
                .forEach(token -> token.revoke(now, reason));

        tokenBlacklistService.revoke(sessionId);
    }
}
