package HK.PrettyWorks_BE.auth.repository;

import HK.PrettyWorks_BE.auth.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    // 재발급 시 제시된 토큰의 행을 찾습니다. 도난 탐지의 출발점이라 폐기된 행도 함께 반환해야 합니다.
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    // 세션(family) 전체 — 도난 탐지·로그아웃 시 한 번에 폐기합니다.
    List<RefreshTokenEntity> findBySessionId(String sessionId);

    // 사용자의 살아 있는 세션들. 세션 개수 제한에서 가장 오래된 것부터 밀어내기 위해 생성 순으로 정렬합니다.
    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(Long userId);

    // 정리 배치: 자연 만료된 행만 삭제합니다.
    // 폐기(revoked)된 행을 지우면 그 토큰이 재사용돼도 "행 없음"으로 떨어져 도난 탐지가 무력화되므로,
    // 만료 시각만 기준으로 삼습니다.
    @Modifying
    @Query("delete from RefreshTokenEntity r where r.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
