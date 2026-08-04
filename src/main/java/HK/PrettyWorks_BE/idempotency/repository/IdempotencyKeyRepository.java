package HK.PrettyWorks_BE.idempotency.repository;

import HK.PrettyWorks_BE.idempotency.domain.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    // 중복 키 예외 후, 별도 트랜잭션에서 기존 기록을 조회해 첫 응답을 재생하거나 409를 판정한다.
    Optional<IdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);

    // 보관 기간이 지난 키를 정리한다. 삭제 건수를 반환.
    // 파생 삭제(deleteByCreatedAtBefore)는 엔티티를 전부 조회한 뒤 한 건씩 지우므로, 벌크 DELETE 한 방으로 처리한다.
    @Modifying
    @Query("delete from IdempotencyKeyEntity k where k.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold);
}
