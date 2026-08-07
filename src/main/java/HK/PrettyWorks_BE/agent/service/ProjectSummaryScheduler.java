package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 매일 새벽 프로젝트 탭 AI 요약을 다시 만듭니다.
 *
 * <p>사용자가 아침에 프로젝트를 열었을 때 이미 어제 기준 배너가 준비돼 있게 하는 것이 목적입니다.
 * 화면 진입 시점에 만들면 첫 사용자만 수 초를 기다리게 됩니다.</p>
 *
 * <p>{@link HK.PrettyWorks_BE.global.scheduler.CleanupScheduler}에 합치지 않은 이유는 성격이
 * 다르기 때문입니다. 그쪽은 멱등한 삭제라 여러 번 돌아도 결과가 같지만, 이쪽은 LLM 호출이라
 * 한 번이 곧 비용입니다 — 실행 주기와 대상을 따로 조절할 수 있어야 합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSummaryScheduler {

    // 진행중·보류만 만든다. 완료·중단된 프로젝트의 배너는 사용자가 볼 일이 없고,
    // 완료 프로젝트가 쌓일수록 매일 밤 LLM 호출이 그만큼 늘어난다.
    private static final List<ProjectStatus> TARGET_STATUSES =
            List.of(ProjectStatus.ONGOING, ProjectStatus.HOLDING);

    private final ProjectRepository projectRepository;
    private final ProjectSummaryService projectSummaryService;

    // 기본값을 둡니다. application.yml 은 gitignore 대상이라 키가 없는 동료의 머신에서
    // 부팅이 깨지기 때문입니다(비밀값이 아니라 정책 상수입니다 — CleanupScheduler 주석 참고).
    @Scheduled(cron = "${agent.summary.cron:0 20 3 * * *}")
    public void generateDailySummaries() {
        List<Long> projectIds = projectRepository.findIdsByStatusIn(TARGET_STATUSES);
        if (projectIds.isEmpty()) {
            return;
        }

        int succeeded = 0;
        for (Long projectId : projectIds) {
            // 한 프로젝트의 실패가 나머지를 막지 않는다. FastAPI가 잠깐 죽어도
            // 다음 프로젝트는 시도해 봐야 하고, 못 만든 프로젝트는 어제 배너가 그대로 남는다.
            try {
                projectSummaryService.generateAsOwner(projectId);
                succeeded++;
            } catch (RuntimeException failure) {
                log.warn("[프로젝트 요약 배치] 생성 실패. projectId={}", projectId, failure);
            }
        }

        log.info("[프로젝트 요약 배치] {}건 중 {}건 생성", projectIds.size(), succeeded);
    }
}
