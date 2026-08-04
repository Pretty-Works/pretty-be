package HK.PrettyWorks_BE.idempotency.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멱등 키 기록. 본 리소스 INSERT와 같은 트랜잭션에서 저장되며, idempotency_key UNIQUE가 중복 요청을 막는다.
// 한 번 쓰이면 변경되지 않는 불변 레코드.
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 메서드 + 실제 경로(path variable 포함) + 본문의 SHA-256. 같은 키·다른 내용(409)을 구분한다.
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    // 생성된 리소스 ID(예: expenseId). 재시도 시 이 값으로 첫 응답을 재생한다.
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    private IdempotencyKeyEntity(String idempotencyKey, String endpoint, Long userId,
                                 String requestHash, Long resourceId) {
        this.idempotencyKey = idempotencyKey;
        this.endpoint = endpoint;
        this.userId = userId;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
    }

    public static IdempotencyKeyEntity of(String idempotencyKey, String endpoint, Long userId,
                                          String requestHash, Long resourceId) {
        return new IdempotencyKeyEntity(idempotencyKey, endpoint, userId, requestHash, resourceId);
    }
}
