package HK.PrettyWorks_BE.auth.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사용자별 refresh token 한 개를 보관하는 1:1 테이블입니다.
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity extends BaseTimeEntity {
    // users.id를 그대로 PK로 사용해 사용자당 토큰을 1개만 유지합니다.
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token", length = 512, nullable = false)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public RefreshTokenEntity(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    // 재발급(RTR) 시 같은 row를 새 토큰 값으로 갱신합니다.
    public void update(String token, LocalDateTime expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }
}
