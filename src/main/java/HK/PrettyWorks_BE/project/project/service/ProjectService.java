package HK.PrettyWorks_BE.project.project.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest.MemberRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest.MilestoneRequest;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectStatusResponse;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.MilestoneRepository;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberService projectMemberService;
    private final MilestoneRepository milestoneRepository;

    @Transactional
    public ProjectResponse create(Long ownerId, ProjectRequest request) {
        // 1) 오너 조회 + 생성 권한 (PROJECT_001)
        //    토큰의 userId로 조회한다. 유저가 없으면 인증 자체를 신뢰할 수 없으므로 UNAUTHORIZED(CurrentUserService).
        UserEntity owner = currentUserService.getCurrentUser(ownerId);
        if (!ProjectPolicy.canCreate(owner)) {
            throw BaseException.type(ProjectErrorCode.NO_CREATE_PERMISSION);
        }

        // 2) 기간 검증 (PROJECT_003)
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validatePeriod(startDate, endDate);

        // 3) 마일스톤 검증 (PROJECT_016 → PROJECT_015)
        List<MilestoneRequest> milestones = cleanMilestones(request.milestones());
        validateMilestones(milestones, startDate, endDate);

        // 4) 참여자 정리(오너 제외 + userId 중복 제거) 후 존재·재직 검증 (PROJECT_002 / USER_001)
        Map<Long, MemberRequest> participants = collectParticipants(request.members(), ownerId);
        validateParticipants(participants.keySet());

        // 5) 저장 (project → members → milestones, 모두 이 트랜잭션 안에서)
        // 5-1) project: 상태는 ONGOING 고정, 예산 미입력(null)은 0(제한 없음)으로 저장
        ProjectEntity project = ProjectEntity.builder()
                .name(request.name())
                .status(ProjectStatus.ONGOING)
                .startDate(startDate)
                .targetDate(endDate)
                .targetBudget(budgetOrZero(request.budget()))
                .description(request.description())
                .build();
        projectRepository.save(project);

        // 5-2) members: 오너(is_owner=true, role=ownerRole) + 참여자(is_owner=false)
        List<ProjectMemberEntity> memberEntities = new ArrayList<>();
        memberEntities.add(ProjectMemberEntity.builder()
                .projectId(project.getId())
                .userId(ownerId)
                .isOwner(true)
                .role(request.ownerRole())
                .status(ProjectMemberStatus.ACTIVE)
                .build());
        for (MemberRequest m : participants.values()) {
            memberEntities.add(ProjectMemberEntity.builder()
                    .projectId(project.getId())
                    .userId(m.userId())
                    .isOwner(false)
                    .role(m.role())
                    .status(ProjectMemberStatus.ACTIVE)
                    .build());
        }
        projectMemberRepository.saveAll(memberEntities);

        // 5-3) milestones
        List<MilestoneEntity> milestoneEntities = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            milestoneEntities.add(MilestoneEntity.builder()
                    .projectId(project.getId())
                    .targetDate(m.targetDate())
                    .goal(m.goal())
                    .build());
        }
        milestoneRepository.saveAll(milestoneEntities);

        // 6) 생성된 프로젝트 id 반환
        return ProjectResponse.builder()
                .projectId(project.getId())
                .build();
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, ProjectRequest request) {
        // 1) 대상 프로젝트 조회 (PROJECT_004)
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 수정 권한 (PROJECT_005): 호출자의 참여중(ACTIVE) 멤버십이 오너이거나 role="PM"
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller)) {
            throw BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) 기간 검증 (PROJECT_003)
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validatePeriod(startDate, endDate);

        // 4) 마일스톤 검증 (PROJECT_016 → PROJECT_015)
        List<MilestoneRequest> milestones = cleanMilestones(request.milestones());
        validateMilestones(milestones, startDate, endDate);

        // 5) 오너 행 조회 — 참여자 제외 기준이자 오너 역할 갱신 대상 (오너는 항상 존재)
        ProjectMemberEntity owner = projectMemberRepository.findOwner(projectId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.INTERNAL_SERVER_ERROR));

        // 6) 요청 참여자 정리(오너 제외 + 중복 제거) 후 존재·재직 검증 (PROJECT_002 / USER_001)
        Map<Long, MemberRequest> requested = collectParticipants(request.members(), owner.getUserId());
        validateParticipants(requested.keySet());

        // 7) 프로젝트 기본정보 수정 (status는 이 API로 바꾸지 않음, 예산 null→0)
        project.update(request.name(), startDate, endDate, budgetOrZero(request.budget()), request.description());

        // 8) 오너 직무 역할 갱신
        owner.updateRole(request.ownerRole());

        // 9) 참여자 diff (추가 insert / 재활성화 / 역할 변경 / 빠짐 soft-delete)
        updateParticipants(projectId, requested);

        // 10) 마일스톤: 현재와 비교해 다르면 전체 교체
        replaceMilestonesIfChanged(projectId, milestones);

        return ProjectResponse.builder()
                .projectId(project.getId())
                .build();
    }

    @Transactional
    public ProjectStatusResponse changeStatus(Long userId, Long projectId, String statusStr) {
        // 1) 대상 프로젝트 조회 (PROJECT_004)
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 상태 변경 권한 (PROJECT_017): 호출자의 참여중(ACTIVE) 멤버십이 오너여야 함
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_STATUS_CHANGE_PERMISSION));
        if (!ProjectPolicy.canChangeStatus(caller)) {
            throw BaseException.type(ProjectErrorCode.NO_STATUS_CHANGE_PERMISSION);
        }

        // 3) 상태 값 파싱 (PROJECT_018)
        ProjectStatus target = parseStatus(statusStr);

        // 4) 전이 규칙 검증 (PROJECT_019): 종료 상태(COMPLETED/ARCHIVED)의 되돌림 차단
        validateTransition(project.getStatus(), target);

        // 5) 상태 변경 (영속 엔티티 → dirty checking으로 UPDATE)
        project.changeStatus(target);

        return ProjectStatusResponse.builder()
                .projectId(project.getId())
                .status(target)
                .build();
    }

    // 종료일이 시작일보다 빠르면 차단 (같은 날은 허용) — PROJECT_003
    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw BaseException.type(ProjectErrorCode.INVALID_PERIOD);
        }
    }

    // 예산 미입력(null)은 0(제한 없음)으로 보정.
    private BigDecimal budgetOrZero(BigDecimal budget) {
        return budget == null ? BigDecimal.ZERO : budget;
    }

    // 마일스톤 목록 null 보정 + 목표일·목표 내용이 둘 다 비어 있는 빈 항목은 무시(제거).
    private List<MilestoneRequest> cleanMilestones(List<MilestoneRequest> milestones) {
        if (milestones == null) {
            return List.of();
        }
        List<MilestoneRequest> result = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            boolean noDate = m.targetDate() == null;
            boolean noGoal = !StringUtils.hasText(m.goal());
            if (noDate && noGoal) {
                continue;   // 둘 다 비면 빈 항목 → 무시
            }
            result.add(m);
        }
        return result;
    }

    // 요청 members에서 오너를 제외하고 userId 중복을 제거한다(입력 순서 유지). 오너는 members에 넣지 않는 규약.
    private Map<Long, MemberRequest> collectParticipants(List<MemberRequest> members, Long ownerId) {
        Map<Long, MemberRequest> participants = new LinkedHashMap<>();
        if (members == null) {
            return participants;
        }
        for (MemberRequest m : members) {
            if (m.userId().equals(ownerId)) {
                continue;
            }
            participants.putIfAbsent(m.userId(), m);
        }
        return participants;
    }

    // 마일스톤 형식(둘 다 입력, PROJECT_016)·기간(프로젝트 기간 내, PROJECT_015)을 검증한다.
    private void validateMilestones(List<MilestoneRequest> milestones, LocalDate startDate, LocalDate endDate) {
        for (MilestoneRequest m : milestones) {
            if (m.targetDate() == null || !StringUtils.hasText(m.goal())) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_INCOMPLETE);
            }
            if (m.targetDate().isBefore(startDate) || m.targetDate().isAfter(endDate)) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_OUT_OF_RANGE);
            }
        }
    }

    // 참여자 userId가 모두 실제로 존재하는지(PROJECT_002)·재직중(ACTIVE)인지(USER_001) 검증한다.
    private void validateParticipants(Set<Long> userIds) {
        List<UserEntity> foundUsers = userRepository.findAllById(userIds);
        if (foundUsers.size() != userIds.size()) {
            throw BaseException.type(ProjectErrorCode.MEMBER_NOT_FOUND);
        }
        for (UserEntity u : foundUsers) {
            if (!UserPolicy.isActive(u)) {
                throw BaseException.type(UserErrorCode.INACTIVE_USER);
            }
        }
    }

    // 참여자 diff (오너 제외). 현재 멤버(ACTIVE+LEFT)와 요청을 userId로 비교한다.
    private void updateParticipants(Long projectId, Map<Long, MemberRequest> requested) {
        Map<Long, ProjectMemberEntity> current = new LinkedHashMap<>();
        for (ProjectMemberEntity m : projectMemberRepository.findParticipants(projectId)) {
            current.put(m.getUserId(), m);
        }

        LocalDateTime now = LocalDateTime.now();
        List<ProjectMemberEntity> toInsert = new ArrayList<>();

        // 요청에 있는 참여자: 신규 insert / LEFT였으면 재활성화 / 참여중이면 역할만 갱신
        for (MemberRequest req : requested.values()) {
            ProjectMemberEntity existing = current.get(req.userId());
            if (existing == null) {
                toInsert.add(ProjectMemberEntity.builder()
                        .projectId(projectId)
                        .userId(req.userId())
                        .isOwner(false)
                        .role(req.role())
                        .status(ProjectMemberStatus.ACTIVE)
                        .build());
            } else if (existing.getStatus() == ProjectMemberStatus.LEFT) {
                existing.reactivate(req.role());
            } else if (!Objects.equals(existing.getRole(), req.role())) {
                existing.updateRole(req.role());
            }
        }

        // 요청에서 빠진 참여중(ACTIVE) 참여자: soft-delete (LEFT였던 사람은 그대로 둠)
        for (ProjectMemberEntity m : current.values()) {
            if (m.getStatus() == ProjectMemberStatus.ACTIVE && !requested.containsKey(m.getUserId())) {
                m.leave(now);
            }
        }

        if (!toInsert.isEmpty()) {
            projectMemberRepository.saveAll(toInsert);
        }
        // 기존 엔티티(reactivate/updateRole/leave)는 영속 상태라 flush 시 dirty checking으로 자동 UPDATE.
    }

    // 마일스톤을 현재와 비교해 내용이 다르면 전체 교체한다(같으면 DB를 건드리지 않음).
    private void replaceMilestonesIfChanged(Long projectId, List<MilestoneRequest> milestones) {
        List<MilestoneEntity> current = milestoneRepository.findByProjectId(projectId);
        if (milestonesEqual(current, milestones)) {
            return;
        }

        if (!current.isEmpty()) {
            milestoneRepository.deleteAll(current);
        }
        List<MilestoneEntity> toInsert = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            toInsert.add(MilestoneEntity.builder()
                    .projectId(projectId)
                    .targetDate(m.targetDate())
                    .goal(m.goal())
                    .build());
        }
        if (!toInsert.isEmpty()) {
            milestoneRepository.saveAll(toInsert);
        }
    }

    // 마일스톤을 (targetDate, goal) 기준으로 정렬해 순서 무관하게 내용이 같은지 비교한다.
    private boolean milestonesEqual(List<MilestoneEntity> current, List<MilestoneRequest> requested) {
        if (current.size() != requested.size()) {
            return false;
        }
        List<MilestoneEntity> a = current.stream()
                .sorted(Comparator.comparing(MilestoneEntity::getTargetDate)
                        .thenComparing(MilestoneEntity::getGoal))
                .toList();
        List<MilestoneRequest> b = requested.stream()
                .sorted(Comparator.comparing(MilestoneRequest::targetDate)
                        .thenComparing(MilestoneRequest::goal))
                .toList();
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getTargetDate().equals(b.get(i).targetDate())
                    || !a.get(i).getGoal().equals(b.get(i).goal())) {
                return false;
            }
        }
        return true;
    }

    // 상태 문자열을 ProjectStatus로 변환한다. 정의된 값이 아니면 PROJECT_018.
    private ProjectStatus parseStatus(String statusStr) {
        try {
            return ProjectStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            throw BaseException.type(ProjectErrorCode.INVALID_STATUS);
        }
    }

    // 종료 상태 전이 규칙: ARCHIVED는 완전 종료, COMPLETED는 ARCHIVED로만 전이 가능(PROJECT_019).
    private void validateTransition(ProjectStatus current, ProjectStatus target) {
        if (current == ProjectStatus.ARCHIVED) {
            throw BaseException.type(ProjectErrorCode.STATUS_NOT_REVERTIBLE);
        }
        if (current == ProjectStatus.COMPLETED && target != ProjectStatus.ARCHIVED) {
            throw BaseException.type(ProjectErrorCode.STATUS_NOT_REVERTIBLE);
        }
    }
}
