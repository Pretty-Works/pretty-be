package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLifecycleScheduler {
    private static final int BATCH_SIZE = 100;

    private final AgentInteractionRepository interactionRepository;
    private final AgentRunRepository runRepository;
    private final AgentRunEventService runEventService;

    @Value("${agent.run.max-duration-minutes:15}")
    private long maxRunDurationMinutes;

    @PostConstruct
    void validateConfiguration() {
        if (maxRunDurationMinutes <= 0) {
            throw new IllegalStateException("agent.run.max-duration-minutes must be positive");
        }
    }

    @Scheduled(fixedDelayString = "${agent.lifecycle.sweep-millis:60000}")
    public void expirePendingInteractions() {
        int expired = 0;
        while (true) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> ids = interactionRepository.findExpiredIds(
                    AgentInteractionStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (runEventService.expireInteraction(id, now).disposition()
                        == AgentRunEventService.Disposition.EXPIRED) {
                    expired++;
                }
            }
        }
        if (expired > 0) {
            log.info("[에이전트 상호작용 만료] {}건 종료", expired);
        }
        expireTimedOutRunningRuns();
    }

    private void expireTimedOutRunningRuns() {
        int expired = 0;
        while (true) {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(maxRunDurationMinutes);
            List<Long> ids = runRepository.findTimedOutIds(
                    AgentRunStatus.RUNNING, threshold, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (runEventService.expireTimedOutRunningRun(id, threshold).disposition()
                        == AgentRunEventService.Disposition.EXPIRED) {
                    expired++;
                }
            }
        }
        if (expired > 0) {
            log.info("[에이전트 실행 시간 초과] {}건 종료", expired);
        }
    }
}
