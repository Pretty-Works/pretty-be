package HK.PrettyWorks_BE.idempotency.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.Sha256;
import HK.PrettyWorks_BE.idempotency.domain.IdempotencyKeyEntity;
import HK.PrettyWorks_BE.idempotency.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

// 멱등 키 공통 처리기. DB 유니크 인덱스로 중복 요청을 막고, 본 리소스 INSERT와 같은 트랜잭션에서 키를 기록한다.
// 트랜잭션 경계를 이 클래스가 소유하므로(TransactionTemplate), 호출 서비스는 @Transactional을 걸지 않는다.
@Service
public class IdempotencyService {

    // idempotency_key 가 VARCHAR(64). 컨트롤러마다 @Size로 두면 복사본이 늘고 빠뜨리기 쉬워 여기서 지킨다.
    private static final int MAX_KEY_LENGTH = 64;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper,
                              PlatformTransactionManager txManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    // 요청 지문을 만듭니다. "메서드 | 실제 경로(path variable 포함) | 요청 본문(JSON)".
    // 본문은 직렬화로 만들기 때문에 DTO에 필드가 늘어도 자동 반영됩니다.
    // (필드를 손으로 이어붙이면 갱신을 빠뜨렸을 때 서로 다른 요청이 같은 지문이 되어 잘못된 응답이 재생됩니다)
    public String fingerprint(String method, String path, Object requestBody) {
        return method + "|" + path + "|" + objectMapper.writeValueAsString(requestBody);
    }

    // 생성 로직을 트랜잭션으로 실행한다. 키 유무 분기·트랜잭션·해싱·409를 모두 여기서 담당하므로,
    // 각 도메인 서비스는 creator(무엇을 저장할지)와 fingerprint(무엇으로 중복 판정할지)만 넘기면 된다.
    //  - 멱등 키 없음 → 멱등 처리 없이 트랜잭션 안에서 생성(하위 호환)
    //  - 멱등 키 있음 → [한 트랜잭션] 본 리소스 insert + 멱등 키 saveAndFlush(중복 즉시 표면화)
    //       · 중복 키 예외(다른 요청이 먼저 커밋) → 그 트랜잭션 롤백(오염) → 별도 트랜잭션으로 기존 기록 조회
    //           - request_hash 일치 → 첫 응답 재생(멱등)
    //           - 불일치        → 같은 키·다른 내용 → 409
    public Long run(String key, String endpoint, Long userId, String fingerprint, Supplier<Long> creator) {
        // 멱등 키 없으면 그냥 트랜잭션 실행
        if (key == null || key.isBlank()) {
            return txTemplate.execute(status -> creator.get());
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        String requestHash = Sha256.hash(fingerprint);
        try {
            return txTemplate.execute(status -> {
                Long resourceId = creator.get();
                repository.saveAndFlush(
                        IdempotencyKeyEntity.of(key, endpoint, userId, requestHash, resourceId));
                return resourceId;
            });
        } catch (DataIntegrityViolationException e) {
            // 오염된 트랜잭션 밖에서, 새 트랜잭션으로 기존 키를 조회한다.
            IdempotencyKeyEntity saved = txTemplate.execute(status ->
                    repository.findByIdempotencyKey(key).orElse(null));

            // 우리 키의 중복이 아니라 다른 무결성 오류였다면(FK 등) 원래 예외를 그대로 전파한다.
            if (saved == null) {
                throw e;
            }
            // 같은 키·같은 내용 → 첫 응답 재생 / 같은 키·다른 내용 → 409
            if (saved.getRequestHash().equals(requestHash)) {
                return saved.getResourceId();
            }
            throw BaseException.type(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }

}
