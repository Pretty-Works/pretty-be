package HK.PrettyWorks_BE.idempotency.service;

import HK.PrettyWorks_BE.idempotency.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 보관 기간이 지난 멱등 키를 주기적으로 삭제합니다.
// 키가 사라진 뒤 같은 키로 요청하면 새로 생성되므로, 멱등은 "무제한"이 아니라 보관 기간 안에서만 보장됩니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyKeyRepository repository;

    @Value("${idempotency.retention-hours}")
    private long retentionHours;

    // 인스턴스가 여러 대면 배치도 여러 번 돌지만, 삭제는 멱등이라 결과가 같습니다.
    @Scheduled(cron = "${idempotency.cleanup-cron}")
    @Transactional
    public void deleteExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(retentionHours);

        int deleted = repository.deleteCreatedBefore(threshold);

        if (deleted > 0) {
            log.info("[멱등키 정리] {} 이전 생성분 {}건 삭제", threshold, deleted);
        }
    }
}
