package HK.PrettyWorks_BE.agent.summary.application;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.summary.dto.AgentProjectSummaryRequest;
import HK.PrettyWorks_BE.agent.summary.dto.AgentProjectSummaryResult;
import HK.PrettyWorks_BE.agent.summary.dto.ProjectSummaryResponse;
import HK.PrettyWorks_BE.agent.summary.gateway.AgentSummaryClient;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로젝트 탭 상단 AI 요약 — 생성·저장·조회.
 *
 * <p>BE의 역할은 게이트입니다. 재료를 모아 FastAPI에 넘기고, 돌아온 배너를 해석하지 않고
 * 저장했다가 프론트에 그대로 돌려줍니다. 문장도 숫자도 BE가 만들지 않습니다.</p>
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않은 것은 의도입니다. FastAPI 호출은 수 초가 걸리므로
 * 트랜잭션 밖에서 해야 합니다({@link ProjectSummaryStore} 주석 참고).</p>
 */
@Slf4j
@Service
public class ProjectSummaryService {

    private final ProjectMemberService projectMemberService;
    private final ProjectSummaryMaterialService materialService;
    private final ProjectSummaryStore summaryStore;
    private final AgentSummaryClient summaryClient;
    private final long minRefreshIntervalMinutes;

    // 같은 프로젝트에 대한 동시 생성을 막는다. 탭 4개를 동시에 여는 것만으로도 LLM 호출이
    // 네 번 나가는데, 결과는 어차피 마지막 하나만 남는다.
    //
    // 인스턴스 메모리라 다중 인스턴스에서는 인스턴스당 한 건까지 허용된다. 여기서 Redis 락까지
    // 끌어오지 않는 이유는 최악의 경우가 "요약을 몇 번 더 만든다"이지 데이터 손상이 아니기 때문이다.
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    public ProjectSummaryService(
            ProjectMemberService projectMemberService,
            ProjectSummaryMaterialService materialService,
            ProjectSummaryStore summaryStore,
            AgentSummaryClient summaryClient,
            @Value("${agent.summary.min-refresh-interval-minutes:10}") long minRefreshIntervalMinutes
    ) {
        if (minRefreshIntervalMinutes < 0) {
            throw new IllegalStateException(
                    "agent.summary.min-refresh-interval-minutes must not be negative");
        }
        this.projectMemberService = projectMemberService;
        this.materialService = materialService;
        this.summaryStore = summaryStore;
        this.summaryClient = summaryClient;
        this.minRefreshIntervalMinutes = minRefreshIntervalMinutes;
    }

    /**
     * 배너를 읽습니다. 프론트가 부르는 것은 사실상 이것 하나입니다 — projectId만 있으면 됩니다.
     *
     * <p>세 갈래로 갈립니다.</p>
     * <ol>
     *   <li>저장된 배너가 없다 → 그 자리에서 만든다(새 프로젝트, 배치 실패 후)</li>
     *   <li>있는데 그사이 재료가 바뀌었다 → 다시 만든다. 단 마지막 생성으로부터
     *       {@code agent.summary.min-refresh-interval-minutes}가 지났을 때만</li>
     *   <li>그 밖에는 저장된 것을 그대로 준다 — LLM 호출 없음, 즉시 응답</li>
     * </ol>
     *
     * <p>재료가 바뀌었는지는 도메인이 알려 주지 않고 여기서 물어봅니다
     * ({@link ProjectSummaryStore#currentStamp}). 할 일 상태 변경·지출 등록·게시글 작성·
     * 에이전트 쓰기 도구까지 무효화 지점이 도메인 전반에 흩어져 있어, 그 자리마다 호출을 심으면
     * 지점이 하나 늘 때마다 빠뜨릴 자리가 하나씩 늘어나기 때문입니다.</p>
     *
     * <p>생성에 실패해도 이 API는 실패하지 않습니다. 배너는 화면의 부가 정보라
     * 에이전트 서버가 죽었다고 프로젝트 페이지 전체가 5xx로 막히면 안 됩니다 —
     * 저장된 것이 있으면 그것을, 없으면 빈 배열을 줍니다.</p>
     *
     * @param section 특정 섹션 한 장만 원할 때. 비우면 4장 전부.
     */
    public ProjectSummaryResponse get(Long userId, Long projectId, String section) {
        projectMemberService.validateAccess(projectId, userId);

        List<ProjectSummaryStore.StoredSummary> stored = summaryStore.load(projectId);
        if (stored.isEmpty() || needsRefresh(projectId, stored)) {
            List<ProjectSummaryStore.StoredSummary> regenerated = generateQuietly(userId, projectId);
            // 실패하면 있던 배너를 그대로 쓴다. 낡은 배너가 배너 없음보다 낫다.
            if (!regenerated.isEmpty()) {
                stored = regenerated;
            }
        }
        return toResponse(projectId, stored, section);
    }

    // 재료가 바뀌었고, 마지막 생성으로부터 최소 간격이 지났을 때만 true.
    //
    // 최소 간격이 없으면 활발한 프로젝트에서 진입할 때마다 LLM이 돈다. 할 일 하나만 체크해도
    // 지문이 달라지기 때문이다. 간격은 "얼마나 최신이어야 하는가"의 가격표다.
    private boolean needsRefresh(Long projectId, List<ProjectSummaryStore.StoredSummary> stored) {
        if (isFresh(stored)) {
            return false;
        }
        // 지문은 해석하지 않고 같은지만 본다. 저장된 값이 null이면(지문 도입 전에 만들어진 배너)
        // 한 번 다시 만들면서 지문이 채워진다.
        String current = summaryStore.currentStamp(projectId);
        boolean changed = !Objects.equals(current, stored.getFirst().sourceStamp());
        if (changed) {
            log.debug("[프로젝트 요약] 재료가 바뀌어 다시 만듭니다. projectId={}", projectId);
        }
        return changed;
    }

    /**
     * 요약을 새로 만듭니다. 조회(read-through)로 충분한 대부분의 경우에는 필요 없고,
     * "방금 바꿨으니 배너도 다시"를 프론트가 명시적으로 요구할 때만 쓰는 API입니다.
     *
     * <p>조회와 달리 실패를 삼키지 않습니다. 사용자가 갱신을 요청한 이상 실패했다는 사실은
     * 알려야 합니다(502·504). 저장된 이전 배너는 그대로 남습니다.</p>
     *
     * <p>마지막 생성으로부터 {@code agent.summary.min-refresh-interval-minutes}가 지나지 않았으면
     * FastAPI를 부르지 않고 저장된 배너를 그대로 돌려줍니다 — 연타를 막는 장치입니다.</p>
     */
    public ProjectSummaryResponse refresh(Long userId, Long projectId) {
        projectMemberService.validateAccess(projectId, userId);

        List<ProjectSummaryStore.StoredSummary> stored = summaryStore.load(projectId);
        if (isFresh(stored)) {
            return toResponse(projectId, stored, null);
        }

        if (!generating.add(projectId)) {
            // 다른 요청이 이미 만들고 있다. 있는 것을 주는 편이 낫고, 첫 생성이라 줄 것이 없을 때만
            // 429로 알린다 — 프론트는 잠시 뒤 조회 API를 다시 부르면 된다.
            log.debug("[프로젝트 요약] 이미 생성 중입니다. projectId={}", projectId);
            if (stored.isEmpty()) {
                throw BaseException.type(AgentErrorCode.SUMMARY_IN_PROGRESS);
            }
            return toResponse(projectId, stored, null);
        }

        try {
            return toResponse(projectId, generate(userId, projectId), null);
        } finally {
            generating.remove(projectId);
        }
    }

    /**
     * 배치용 생성. 요청 사용자가 없으므로 오너의 자격으로 재료를 읽습니다.
     * 최소 간격 검사를 하지 않습니다 — 하루 한 번 도는 배치는 그 자체가 간격입니다.
     */
    public void generateAsOwner(Long projectId) {
        Long ownerId = projectMemberService.getOwnerId(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!generating.add(projectId)) {
            log.debug("[프로젝트 요약] 이미 생성 중이라 배치를 건너뜁니다. projectId={}", projectId);
            return;
        }
        try {
            generate(ownerId, projectId);
        } finally {
            generating.remove(projectId);
        }
    }

    // ================================= 내부 =================================

    // 조회가 부르는 생성. 실패해도 예외를 밖으로 내보내지 않는다 —
    // 배너가 없는 화면은 멀쩡하지만, 배너 때문에 500이 나는 화면은 못 쓴다.
    // 빈 목록을 돌려주면 호출자가 저장된 배너를 그대로 쓴다.
    private List<ProjectSummaryStore.StoredSummary> generateQuietly(Long userId, Long projectId) {
        if (!generating.add(projectId)) {
            // 다른 요청이 이미 만들고 있다. 탭 4개를 동시에 여는 경우가 대표적이다.
            // 기다리지 않는다 — 잠시 뒤 다시 조회하면 새것이 나온다.
            log.debug("[프로젝트 요약] 이미 생성 중이라 기다리지 않습니다. projectId={}", projectId);
            return List.of();
        }
        try {
            return generate(userId, projectId);
        } catch (RuntimeException failure) {
            log.warn("[프로젝트 요약] 생성에 실패했습니다. projectId={}", projectId, failure);
            return List.of();
        } finally {
            generating.remove(projectId);
        }
    }

    // 재료 수집(짧은 트랜잭션) → FastAPI 호출(트랜잭션 밖) → 저장(짧은 트랜잭션).
    private List<ProjectSummaryStore.StoredSummary> generate(Long viewerId, Long projectId) {
        LocalDate today = LocalDate.now();

        // 지문을 재료보다 먼저 읽는다. 순서가 반대면, 재료를 읽은 뒤 지문을 읽기까지의 사이에
        // 들어온 변경이 지문에는 잡히고 재료에는 빠진다 — 그러면 낡은 배너가 최신으로 표시되고
        // 다음 조회도 "안 바뀌었다"고 판정해 영영 갱신되지 않는다.
        String sourceStamp = summaryStore.currentStamp(projectId);
        AgentProjectSummaryRequest materials = materialService.collect(viewerId, projectId, today);

        AgentProjectSummaryResult result = summaryClient.generate(materials);

        LocalDateTime generatedAt = LocalDateTime.now();
        summaryStore.save(projectId, result, generatedAt, sourceStamp);
        log.info("[프로젝트 요약] 생성 완료. projectId={} sections={}",
                projectId, result.summaries().size());

        // 저장한 것을 다시 읽지 않는다. 방금 만든 값이 곧 응답이다.
        return result.summaries().stream()
                .map(summary -> new ProjectSummaryStore.StoredSummary(
                        summary.section(), summary.payload(), generatedAt, sourceStamp))
                .toList();
    }

    // 마지막 생성으로부터 최소 간격이 지나지 않았으면 재료가 바뀌었어도 다시 만들지 않는다.
    private boolean isFresh(List<ProjectSummaryStore.StoredSummary> stored) {
        if (stored.isEmpty()) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minRefreshIntervalMinutes);
        return latestGeneratedAt(stored).isAfter(threshold);
    }

    private ProjectSummaryResponse toResponse(Long projectId,
                                              List<ProjectSummaryStore.StoredSummary> stored,
                                              String section) {
        List<ProjectSummaryStore.StoredSummary> selected = filterBySection(stored, section);
        List<JsonNode> banners = selected.stream()
                .map(ProjectSummaryStore.StoredSummary::banner)
                .toList();

        return new ProjectSummaryResponse(
                projectId,
                selected.isEmpty() ? null : latestGeneratedAt(selected),
                banners);
    }

    // 섹션 이름을 enum으로 못 박지 않는다 — BE는 섹션의 의미를 모르고 키로만 쓰기 때문에,
    // LLM팀이 섹션을 하나 더 만들어도 BE 배포 없이 그대로 조회된다.
    // 대신 모르는 값이 오면 400으로 끊는다. 빈 배열을 주면 프론트가 오타를 "아직 생성 전"으로 읽는다.
    private List<ProjectSummaryStore.StoredSummary> filterBySection(
            List<ProjectSummaryStore.StoredSummary> stored, String section) {
        if (!StringUtils.hasText(section)) {
            return stored;
        }
        String wanted = section.trim();
        List<ProjectSummaryStore.StoredSummary> matched = stored.stream()
                .filter(summary -> summary.section().equalsIgnoreCase(wanted))
                .toList();
        if (matched.isEmpty() && !stored.isEmpty()) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        return matched;
    }

    private LocalDateTime latestGeneratedAt(List<ProjectSummaryStore.StoredSummary> stored) {
        return stored.stream()
                .map(ProjectSummaryStore.StoredSummary::generatedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }
}
