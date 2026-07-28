package HK.PrettyWorks_BE.project.project.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.lock.VersionGuard;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
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
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectListResponse;
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
import HK.PrettyWorks_BE.user.service.UserService;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    // 목록 필터에서 "상태를 가리지 않음"을 뜻하는 값. ProjectStatus에는 없는 조회 전용 키워드다.
    private static final String STATUS_FILTER_ALL = "ALL";

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberService projectMemberService;
    private final MilestoneRepository milestoneRepository;
    private final IdempotencyService idempotencyService;
    // 기간 축소 시 하위 데이터를 검사할 도메인들. 각 도메인이 구현체를 등록하고 Spring이 모아서 주입한다.
    private final List<ProjectPeriodConstraint> periodConstraints;

    // 프로젝트 생성. 멱등 키가 있으면 중복 생성을 방어한다(같은 키·같은 요청은 첫 응답 재생, 다른 내용은 409).
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않는다.
    public ProjectResponse create(Long ownerId, String idempotencyKey, ProjectRequest request) {
        // 도메인 조각만 준비: 무엇을 저장할지(creator) + 무엇으로 중복 판정할지(fingerprint).
        Supplier<Long> creator = () -> doCreate(ownerId, request);
        String endpoint = "POST /api/v1/projects";
        String fingerprint = idempotencyService.fingerprint("POST", "/api/v1/projects", request);

        return ProjectResponse.builder()
                .projectId(idempotencyService.run(idempotencyKey, endpoint, ownerId, fingerprint, creator))
                .build();
    }

    // 검증 + 저장(project → members → milestones) 후 생성된 프로젝트 id 반환.
    // 트랜잭션은 IdempotencyService의 TransactionTemplate이 제공한다(self-invocation 프록시 함정 회피).
    private Long doCreate(Long ownerId, ProjectRequest request) {
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

        // 4) 참여자 정리(오너 제외 + userId 중복 제거) 후 존재·퇴사 여부 검증 (PROJECT_002 / USER_003)
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
        return project.getId();
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, Long version, ProjectRequest request) {
        // 1) 대상 프로젝트 조회 (PROJECT_004)
        //    오너·PM이 동시에 수정할 수 있으므로 낙관적 락으로 조회한다. 커밋 시 version이 강제로 올라가며,
        //    그 사이 다른 트랜잭션이 먼저 커밋했다면 충돌로 실패한다(REQUEST_029, 전역 핸들러가 변환).
        ProjectEntity project = projectRepository.findByIdWithOptimisticLock(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 수정 권한 (PROJECT_005): 호출자의 참여중(ACTIVE) 멤버십이 오너이거나 role="PM"
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller)) {
            throw BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) 종료된 프로젝트는 수정 불가 (PROJECT_020). 보관(ARCHIVED)은 소프트 삭제라 내용이 바뀌면 안 되고,
        //    완료(COMPLETED)도 확정된 기록으로 본다. 버전 검사보다 먼저 해야 원인이 명확한 에러가 나간다.
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(ProjectErrorCode.PROJECT_CLOSED);
        }

        // 4) 선행 조건 검증 (REQUEST_029): 클라이언트가 상세 조회에서 받은 version과 현재 version이 같아야 한다.
        //    수정 폼을 열어둔 사이 다른 사용자가 먼저 저장했다면 여기서 걸린다.
        //    (거의 같은 순간 도착한 요청은 이 검사를 둘 다 통과하므로, 커밋 시점의 낙관적 락이 뒤늦은 쪽을 막는다)
        VersionGuard.validate(project.getVersion(), version);

        // 5) 기간 검증 (PROJECT_003)
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validatePeriod(startDate, endDate);

        // 6) 기간을 줄이는 경우, 새 기간을 벗어나 남게 되는 하위 데이터가 있으면 차단 (PROJECT_021)
        validatePeriodShrink(project, startDate, endDate);

        // 7) 마일스톤 검증 (PROJECT_016 → PROJECT_015)
        List<MilestoneRequest> milestones = cleanMilestones(request.milestones());
        validateMilestones(milestones, startDate, endDate);

        // 8) 오너 행 조회 — 참여자 제외 기준이자 오너 역할 갱신 대상 (오너는 항상 존재)
        ProjectMemberEntity owner = projectMemberRepository.findOwner(projectId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.INTERNAL_SERVER_ERROR));

        // 9) 요청 참여자 정리(오너 제외 + 중복 제거) 후 존재·퇴사 여부 검증 (PROJECT_002 / USER_003)
        Map<Long, MemberRequest> requested = collectParticipants(request.members(), owner.getUserId());
        validateParticipants(requested.keySet());

        // 10) 프로젝트 기본정보 수정 (status는 이 API로 바꾸지 않음, 예산 null→0)
        project.update(request.name(), startDate, endDate, budgetOrZero(request.budget()), request.description());

        // 11) 오너 직무 역할 갱신
        owner.updateRole(request.ownerRole());

        // 12) 참여자 diff (추가 insert / 재활성화 / 역할 변경 / 빠짐 soft-delete)
        updateParticipants(projectId, requested);

        // 13) 마일스톤: 현재와 비교해 다르면 전체 교체
        replaceMilestonesIfChanged(projectId, milestones);

        return ProjectResponse.builder()
                .projectId(project.getId())
                .build();
    }

    // 내가 참여중인 프로젝트 목록. 홈의 '진행 중 프로젝트' 패널과 프로젝트 선택 팝업이 함께 사용한다.
    // 필터·검색·정렬·페이징을 모두 서버가 처리한다 — 클라이언트가 페이지 안에서 거르면 페이지 크기·전체 개수가
    // 어긋나고, 무엇보다 걸러낼 데이터(누적된 완료 프로젝트)까지 전부 전송하게 된다.
    @Transactional(readOnly = true)
    public PageResponse<ProjectListResponse> getMyProjects(Long userId, String statusParam,
                                                           String keyword, Pageable pageable) {
        // 1) 상태 필터 해석 — 미지정은 진행중, ALL은 필터 없음(null), ARCHIVED는 조회 불가
        ProjectStatus status = parseFilterStatus(statusParam);

        // 2) 검색어 정리 — 공백만 들어오면 검색하지 않은 것으로 본다
        String searchKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<ProjectEntity> projects = projectRepository.findMyProjects(
                userId, ProjectMemberStatus.ACTIVE, ProjectStatus.ARCHIVED,
                status, searchKeyword,
                ProjectStatus.ONGOING, ProjectStatus.HOLDING,
                pageable);

        // 3) 진행률은 조회 시점 날짜로 계산한 파생값 (상세 조회와 동일한 계산)
        LocalDate today = LocalDate.now();

        return PageResponse.from(projects.map(project -> ProjectListResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .status(project.getStatus())
                .targetDate(project.getTargetDate())
                .progress(project.calculateProgress(today))
                .build()));
    }

    // 목록 필터용 상태 해석. 값이 없으면 진행중(홈의 기본 화면), ALL이면 상태 조건을 걸지 않는다.
    // ARCHIVED는 소프트 삭제에 해당하므로 명시적으로 요청해도 조회할 수 없다.
    private ProjectStatus parseFilterStatus(String statusParam) {
        if (!StringUtils.hasText(statusParam)) {
            return ProjectStatus.ONGOING;
        }
        if (STATUS_FILTER_ALL.equalsIgnoreCase(statusParam)) {
            return null;
        }

        ProjectStatus status = parseStatus(statusParam);
        if (status == ProjectStatus.ARCHIVED) {
            throw BaseException.type(ProjectErrorCode.INVALID_STATUS);
        }

        return status;
    }

    // 수정 화면 진입용 상세 조회. 참여중(ACTIVE) 멤버면 누구나 조회할 수 있다(수정 권한과 달리 역할을 보지 않음).
    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(Long userId, Long projectId) {
        // 1) 대상 프로젝트 조회 (PROJECT_004)
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 조회 권한 — 참여중 멤버만 (MEMBER_001)
        projectMemberService.validateActiveMember(projectId, userId);

        // 3) 참여중 멤버 전체를 한 번에 조회해 오너/참여자로 나눈다. (참여 순 고정)
        List<ProjectMemberEntity> activeMembers =
                projectMemberRepository.findByProjectIdAndStatusOrderByIdAsc(projectId, ProjectMemberStatus.ACTIVE);

        ProjectMemberEntity ownerMember = null;
        List<ProjectMemberEntity> participants = new ArrayList<>();
        for (ProjectMemberEntity m : activeMembers) {
            if (m.isOwner()) {
                ownerMember = m;
            } else {
                participants.add(m);
            }
        }
        if (ownerMember == null) {
            throw BaseException.type(GlobalErrorCode.INTERNAL_SERVER_ERROR);   // 오너는 항상 존재해야 한다
        }

        // 4) 멤버 이름을 한 번에 조회 (userId → name). 루프 안에서 개별 조회하지 않는다.
        Set<Long> memberUserIds = activeMembers.stream()
                .map(ProjectMemberEntity::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = userService.getNameMap(memberUserIds);

        // 5) 마일스톤 (목표일 오름차순)
        List<MilestoneEntity> milestones = milestoneRepository.findByProjectIdOrderByTargetDateAsc(projectId);

        // 6) 조립 — progress는 오늘 날짜로 계산한 파생값
        ProjectDetailResponse.Owner owner = ProjectDetailResponse.Owner.builder()
                .userId(ownerMember.getUserId())
                .name(nameById.get(ownerMember.getUserId()))
                .ownerRole(ownerMember.getRole())
                .build();

        List<ProjectDetailResponse.Member> memberDtos = participants.stream()
                .map(m -> ProjectDetailResponse.Member.builder()
                        .userId(m.getUserId())
                        .name(nameById.get(m.getUserId()))
                        .role(m.getRole())
                        .build())
                .toList();

        List<ProjectDetailResponse.Milestone> milestoneDtos = milestones.stream()
                .map(m -> ProjectDetailResponse.Milestone.builder()
                        .milestoneId(m.getId())
                        .targetDate(m.getTargetDate())
                        .goal(m.getGoal())
                        .build())
                .toList();

        return ProjectDetailResponse.builder()
                .projectId(project.getId())
                .version(project.getVersion())
                .name(project.getName())
                .startDate(project.getStartDate())
                .endDate(project.getTargetDate())
                .budget(project.getTargetBudget())
                .description(project.getDescription())
                .status(project.getStatus())
                .progress(project.calculateProgress(LocalDate.now()))
                .owner(owner)
                .members(memberDtos)
                .milestones(milestoneDtos)
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
        if (!ProjectPolicy.isAllowedTransition(project.getStatus(), target)) {
            throw BaseException.type(ProjectErrorCode.STATUS_NOT_REVERTIBLE);
        }

        // 5) 상태 변경 (영속 엔티티 → dirty checking으로 UPDATE)
        project.changeStatus(target);

        return ProjectStatusResponse.builder()
                .projectId(project.getId())
                .status(target)
                .build();
    }

    // 기간을 "줄일 때만" 하위 데이터를 확인한다(PROJECT_021). 기간이 그대로거나 넓어지면
    // 기존 데이터는 새 기간에 그대로 포함되므로 조회 자체를 생략한다.
    // 검사 대상(할 일·지출·회의록)은 각 도메인이 ProjectPeriodConstraint로 등록하며, 여기선 구현체를 모른다.
    private void validatePeriodShrink(ProjectEntity project, LocalDate startDate, LocalDate endDate) {
        boolean shrinks = startDate.isAfter(project.getStartDate()) || endDate.isBefore(project.getTargetDate());
        if (!shrinks) {
            return;
        }

        for (ProjectPeriodConstraint constraint : periodConstraints) {
            if (constraint.hasDataOutsidePeriod(project.getId(), startDate, endDate)) {
                throw BaseException.type(ProjectErrorCode.PERIOD_SHRINK_BLOCKED);
            }
        }
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
            if (!ProjectPolicy.isWithinPeriod(startDate, endDate, m.targetDate())) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_OUT_OF_RANGE);
            }
        }
    }

    // 참여자 userId가 모두 실제로 존재하는지(PROJECT_002)·퇴사자가 아닌지(USER_003) 검증한다.
    // 휴직(ON_LEAVE)은 참여를 허용한다 — 복귀 예정자를 미리 배정하거나 기존 참여를 유지할 수 있어야 하므로.
    private void validateParticipants(Set<Long> userIds) {
        List<UserEntity> foundUsers = userRepository.findAllById(userIds);
        if (foundUsers.size() != userIds.size()) {
            throw BaseException.type(ProjectErrorCode.MEMBER_NOT_FOUND);
        }
        for (UserEntity u : foundUsers) {
            if (!UserPolicy.isEmployed(u)) {
                throw BaseException.type(UserErrorCode.RESIGNED_USER);
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

}
