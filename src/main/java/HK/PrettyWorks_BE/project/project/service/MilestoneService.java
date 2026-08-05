package HK.PrettyWorks_BE.project.project.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.util.Percent;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneListResponse;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneStatusResponse;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneSummary;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.MilestoneRepository;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 마일스톤 조회·완료 토글 담당. 생성·수정·삭제는 프로젝트 수정 API(ProjectService.syncMilestones)가 맡는다.
// 편집 경로를 한 곳으로 모아 두어야 완료 상태 보존 규칙이 갈라지지 않는다.
@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final CurrentUserService currentUserService;

    // 마일스톤 목록 + 완료 집계. 개요 화면의 '마일스톤 완료율' 카드를 채운다.
    // 페이지네이션을 두지 않는다 — 프로젝트당 최대 50개이고 화면이 타임라인으로 전부 보여준다.
    @Transactional(readOnly = true)
    public MilestoneListResponse getMilestones(Long projectId, Long userId) {
        // 1) 프로젝트 존재(PROJECT_004) + 조회 권한(MEMBER_001). 참여자면 누구나 볼 수 있다.
        projectMemberService.validateAccess(projectId, userId);

        // 2) 목표일 오름차순 한 번의 조회로 목록·집계·목표 마일스톤을 모두 만든다.
        List<MilestoneEntity> milestones = milestoneRepository.findByProjectIdOrderByTargetDateAscIdAsc(projectId);

        int total = milestones.size();
        int completed = (int) milestones.stream().filter(MilestoneEntity::isDone).count();

        // 3) 목표 마일스톤 = 미완료 중 첫 번째. 이미 정렬돼 있어 목표일이 가장 이른 것이 먼저 걸린다.
        MilestoneListResponse.NextMilestone next = milestones.stream()
                .filter(m -> !m.isDone())
                .findFirst()
                .map(m -> MilestoneListResponse.NextMilestone.builder()
                        .milestoneId(m.getId())
                        .targetDate(m.getTargetDate())
                        .goal(m.getGoal())
                        .build())
                .orElse(null);

        return MilestoneListResponse.builder()
                .totalCount(total)
                .completedCount(completed)
                .pendingCount(total - completed)
                .completionRate(Percent.floorRate(completed, total))
                .nextMilestone(next)
                .milestones(milestones.stream()
                        .map(MilestoneSummary::from)
                        .toList())
                .build();
    }

    // 완료 토글. 프로젝트 수정·상태변경과 같은 권한(오너·PM)을 요구한다 —
    // 마일스톤에는 담당자가 없어 "본인 것만"이 성립하지 않고, 완료 체크가 곧 프로젝트 완료율이기 때문이다.
    @Transactional
    public MilestoneStatusResponse toggleStatus(Long projectId, Long milestoneId, Long userId, boolean done) {
        // 1) 대상 프로젝트 조회 (PROJECT_004) — 종료 여부 판정에 엔티티가 필요하다.
        //    프로젝트 version은 올리지 않는다(findByIdWithOptimisticLock을 쓰지 않는다).
        //    수정 API가 다루는 것은 '계획'(이름·기간·멤버·마일스톤 목록)이고 이 API는 '진행 기록'이라 서로 충돌하지 않는다.
        //    version을 올리면 누가 체크만 해도 열려 있던 수정 화면의 저장이 409로 깨지는데, 그 불편이 막는 사고보다 잦다.
        //    완료 시각 자체는 수정 API가 건드리지 않으므로(MilestoneEntity.update) 덮어쓰일 위험이 없다.
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 변경 권한 (PROJECT_005): 호출자의 참여중(ACTIVE) 멤버십이 오너이거나 role="PM"
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller)) {
            throw BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION);
        }

        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단.
        // 완료 체크가 곧 완료율이라, 퇴사한 오너·PM이 지표를 움직이면 안 된다.
        currentUserService.getEmployedUser(userId);

        // 3) 완료·보관된 프로젝트는 변경 불가 (PROJECT_020)
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(ProjectErrorCode.PROJECT_CLOSED);
        }

        // 4) 마일스톤 존재 + 프로젝트 소속 (PROJECT_022)
        MilestoneEntity milestone = milestoneRepository.findByIdAndProjectId(milestoneId, projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.MILESTONE_NOT_FOUND));

        // 5) 토글 (dirty checking으로 UPDATE). 이미 완료면 최초 완료 시각을 유지한다.
        boolean wasDone = milestone.isDone();
        milestone.toggleDone(done, LocalDateTime.now());

        // 6) 이미 로드된 엔티티의 값만 돌려준다. 변경 후 완료율이 필요하면 호출자가 getMilestones를 부른다 —
        //    화면은 쓰지 않는 숫자라 여기서 세면 모든 토글에 조회가 하나씩 붙는다.
        return MilestoneStatusResponse.builder()
                .milestoneId(milestone.getId())
                .goal(milestone.getGoal())
                .completed(milestone.isDone())
                .completedAt(milestone.getCompletedAt())
                .changed(wasDone != milestone.isDone())
                .build();
    }
}
