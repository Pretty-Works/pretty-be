package HK.PrettyWorks_BE.auth.domain;

import HK.PrettyWorks_BE.auth.constant.RevokeReason;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 로그인 세션에서 발급된 refresh token 한 개를 나타냅니다.
// 한 사용자가 기기별로 여러 세션을 가질 수 있고, 한 세션(sessionId) 안에서 토큰이 회전하며 여러 행이 쌓입니다.
// 회전된 행은 삭제하지 않고 revokedAt만 남깁니다 — 그래야 "폐기된 토큰의 재사용 = 도난"을 탐지할 수 있습니다.
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 로그인 1회 = 세션 1개(family). 토큰이 회전해도 유지되며 access token의 sid claim과 같은 값입니다.
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 원문을 저장하지 않습니다. DB가 유출돼도 토큰을 그대로 사용할 수 없어야 합니다.
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 회전·로그아웃·도난 무효화로 폐기된 시각. null이면 아직 유효한 토큰입니다.
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // 폐기 사유. 회전(ROTATED)된 토큰의 재사용만 도난으로 보고, 로그아웃된 토큰의 재시도는 정상 흐름으로 처리합니다.
    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 20)
    private RevokeReason revokeReason;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    // 이 토큰으로 재발급이 일어난 시각. "로그인 기기 목록"에서 마지막 사용 시점으로 보여줍니다.
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder
    public RefreshTokenEntity(String sessionId, Long userId, String tokenHash,
                              LocalDateTime expiresAt, String userAgent) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    // 회전·로그아웃·도난 무효화 공통. 이미 폐기됐다면 최초 시각과 사유를 유지합니다.
    public void revoke(LocalDateTime now, RevokeReason reason) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
            this.revokeReason = reason;
        }
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    // 정상 회전으로 폐기된 토큰이 다시 사용되면 누군가 옛 토큰을 들고 있다는 뜻입니다.
    // 로그아웃 등으로 끊긴 토큰의 재시도는 도난이 아니라 뒤늦은 요청일 뿐입니다.
    public boolean isReuseOfRotatedToken() {
        return this.revokeReason == RevokeReason.ROTATED;
    }

    // 이 토큰으로 재발급이 성공했을 때 기록합니다.
    public void markUsed(LocalDateTime now) {
        this.lastUsedAt = now;
    }
}
