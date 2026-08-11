package HK.PrettyWorks_BE.global.scheduler;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.auth.repository.RefreshTokenRepository;
import HK.PrettyWorks_BE.idempotency.repository.IdempotencyKeyRepository;
import HK.PrettyWorks_BE.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

// 수명이 다한 운영 데이터를 주기적으로 지웁니다.
// 도메인마다 스케줄러를 흩어놓으면 "언제 무엇이 지워지는지"를 한눈에 볼 수 없어 한 곳에 모았습니다.
// 인스턴스가 여러 대면 배치도 여러 번 돌지만, 삭제는 멱등이라 결과가 같습니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {
    private static final int AGENT_EVENT_DELETE_BATCH_SIZE = 100;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentEventRepository agentEventRepository;
    private final NotificationRepository notificationRepository;

    @Value("${idempotency.retention-hours}")
    private long idempotencyRetentionHours;

    @Value("${agent.events.retention-hours}")
    private long agentEventRetentionHours;

    // 다른 보관 기간과 달리 기본값을 둡니다. application.yml 은 gitignore 대상이라
    // 키가 없는 동료의 머신에서 부팅이 깨지기 때문입니다(비밀값이 아니라 정책 상수입니다).
    @Value("${notification.retention-days:90}")
    private long notificationRetentionDays;

    @Scheduled(cron = "${cleanup.cron}")
    @Transactional
    public void cleanup() {
        deleteExpiredIdempotencyKeys();
        deleteExpiredRefreshTokens();
        deleteExpiredAgentEvents();
        deleteExpiredNotifications();
    }

    // 보관 기간이 지난 멱등 키. 키가 사라진 뒤 같은 키로 요청하면 새로 생성되므로,
    // 멱등은 "무제한"이 아니라 보관 기간 안에서만 보장됩니다.
    private void deleteExpiredIdempotencyKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(idempotencyRetentionHours);

        int deleted = idempotencyKeyRepository.deleteCreatedBefore(threshold);

        if (deleted > 0) {
            log.info("[멱등키 정리] {} 이전 생성분 {}건 삭제", threshold, deleted);
        }
    }

    // 자연 만료된 refresh token만 지웁니다.
    // 폐기(revoked)된 행은 남겨야 합니다 — 지우면 그 토큰이 재사용돼도 "행 없음"으로 떨어져
    // 도난 탐지가 무력화되기 때문입니다. 폐기된 행도 만료 시각이 지나면 여기서 함께 정리됩니다.
    private void deleteExpiredRefreshTokens() {
        LocalDateTime now = LocalDateTime.now();

        int deleted = refreshTokenRepository.deleteExpiredBefore(now);

        if (deleted > 0) {
            log.info("[세션 정리] 만료된 refresh token {}건 삭제", deleted);
        }
    }

    // 오래된 알림. 드롭다운은 최근 것만 보므로 무한히 쌓아둘 이유가 없습니다.
    // 안 읽은 알림도 함께 지웁니다 — 90일이 지나도록 확인하지 않았다면 이미 지난 소식입니다.
    private void deleteExpiredNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(notificationRetentionDays);

        int deleted = notificationRepository.deleteCreatedBefore(threshold);

        if (deleted > 0) {
            log.info("[알림 정리] {} 이전 생성분 {}건 삭제", threshold, deleted);
        }
    }

    // 종료 이벤트는 Last-Event-ID 재접속을 위해 1시간 보존한 뒤에만 정리한다.
    private void deleteExpiredAgentEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(agentEventRetentionHours);
        int deletedTotal = 0;
        while (true) {
            List<Long> runIds = agentRunRepository.findTerminalIdsFinishedBefore(
                    AgentRunStatus.terminalStatuses(), threshold,
                    PageRequest.of(0, AGENT_EVENT_DELETE_BATCH_SIZE));
            if (runIds.isEmpty()) {
                break;
            }
            deletedTotal += agentEventRepository.deleteByRunIds(runIds);
        }
        if (deletedTotal > 0) {
            log.info("[에이전트 이벤트 정리] {} 이전 종료 실행의 이벤트 {}건 삭제", threshold, deletedTotal);
        }
    }
}
