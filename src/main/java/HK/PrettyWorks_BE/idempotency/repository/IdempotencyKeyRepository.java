package HK.PrettyWorks_BE.idempotency.repository;

import HK.PrettyWorks_BE.idempotency.domain.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    // 중복 키 예외 후, 별도 트랜잭션에서 기존 기록을 조회해 첫 응답을 재생하거나 409를 판정한다.
    Optional<IdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);
}
