package HK.PrettyWorks_BE.project.project.service;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.lock.VersionGuard;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.notification.constant.NotificationTarget;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.event.NotificationPublisher;
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
import HK.PrettyWorks_BE.project.project.dto.res.*;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.MilestoneRepository;
import HK.PrettyWorks_BE.project.project.repository.MyProjectRow;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final NotificationPublisher notificationPublisher;
    // 기간 축소 시 하위 데이터를 검사할 도메인들. 각 도메인이 구현체를 등록하고 Spring이 모아서 주입한다.
    private final List<ProjectPeriodConstraint> periodConstraints;

    // 프로젝트 생성. 멱등 키가 있으면 중복 생성을 방어한다(같은 키·같은 요청은 첫 응답 재생, 다른 내용은 409).
    // 트랜잭션은 IdempotencyService가 소유하므로 여기엔 @Transactional을 걸지 않는다.
    public ProjectResponse create(Long ownerId, String idempotencyKey, ProjectRequest request) {
        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단.
        // 멱등 처리 '바깥'에 둔다. 안에 두면 어차피 거부할 요청이 멱등 키로 기록돼,
        // 복직 등으로 상태가 풀린 뒤 같은 키로 재시도해도 첫 실패가 그대로 재생된다.
        currentUserService.getEmployedUser(ownerId);

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

        // 5-3) milestones — 새 프로젝트라 전부 신규다. 요청에 milestoneId가 실려 와도 읽지 않고 무시한다.
        List<MilestoneEntity> milestoneEntities = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            milestoneEntities.add(MilestoneEntity.builder()
                    .projectId(project.getId())
                    .targetDate(m.targetDate())
                    .goal(m.goal())
                    .build());
        }
        milestoneRepository.saveAll(milestoneEntities);

        // 6) 참여자에게 추가 알림. 수정으로 추가하든 생성 때 넣든 당사자에겐 같은 일이라 문구도 같다.
        //    create가 아니라 여기 두는 이유: 멱등 재시도는 저장을 건너뛰고 첫 응답을 재생하므로,
        //    바깥에 두면 프로젝트는 하나인데 알림만 여러 번 나간다.
        //    participants는 collectParticipants가 오너를 이미 빼둔 목록이다.
        notificationPublisher.publish(NotificationType.PROJECT_MEMBER_ADDED,
                participants.keySet(), ownerId, NotificationTarget.project(project.getId()),
                project.getName());

        // 7) 생성된 프로젝트 id 반환
        return project.getId();
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, Long version, ProjectRequest request) {
        // 1) 대상 프로젝트 조회 (PROJECT_004)
        //    오너·PM이 동시에 수정할 수 있으므로 낙관적 락으로 조회한다. 커밋 시 version이 강제로 올라가며,
        //    그 사이 다른 트랜잭션이 먼저 커밋했다면 충돌로 실패한다(REQUEST_029, 전역 핸들러가 변환).
        ProjectEntity project = projectRepository.findByIdWithOptimisticLock(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 2) 수정 권한 (PROJECT_005): 참여중(ACTIVE) 멤버이면서 오너이거나 부서가 PM.
        //    재직 검증 없이 읽는다 — 퇴사자에게 USER_003보다 403이 먼저 나가야 한다.
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller, currentUserService.getCurrentUser(userId))) {
            throw BaseException.type(ProjectErrorCode.NO_EDIT_PERMISSION);
        }

        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단.
        // 권한 검증 다음에 둬서, 남의 프로젝트를 건드리는 요청은 403이 먼저 나가게 한다.
        currentUserService.getEmployedUser(userId);

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
        //     기간이 실제로 바뀐 경우에만 알리려고 덮어쓰기 전 값을 잡아둔다.
        boolean periodChanged = !startDate.equals(project.getStartDate())
                || !endDate.equals(project.getTargetDate());
        project.update(request.name(), startDate, endDate, budgetOrZero(request.budget()), request.description());

        // 11) 오너 직무 역할 갱신
        owner.updateRole(request.ownerRole());

        // 12) 참여자 diff (추가 insert / 재활성화 / 역할 변경 / 빠짐 soft-delete) + 추가·제외 알림
        updateParticipants(project, userId, requested);

        // 13) 마일스톤 diff (신규 insert / 기존 갱신 / 요청에서 빠진 것 삭제) — 완료 상태는 보존
        syncMilestones(projectId, milestones);

        // 14) 기간 변경 알림. 12)에서 방금 빠진 사람은 이미 ACTIVE가 아니라 대상에서 자연히 제외된다.
        if (periodChanged) {
            notificationPublisher.publish(NotificationType.PROJECT_PERIOD_CHANGED,
                    projectMemberService.getActiveMemberIds(projectId), userId, NotificationTarget.project(projectId),
                    project.getName(), startDate, endDate);
        }

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
        Page<MyProjectRow> rows = findMyProjects(userId, statusParam, keyword, pageable);

        // 3) 진행률은 조회 시점 날짜로 계산한 파생값 (상세 조회와 동일한 계산)
        LocalDate today = LocalDate.now();

        return PageResponse.from(rows.map(row -> {
            ProjectEntity project = row.project();
            return ProjectListResponse.builder()
                    .projectId(project.getId())
                    .name(project.getName())
                    .status(project.getStatus())
                    .targetDate(project.getTargetDate())
                    .progress(project.calculateProgress(today))
                    .build();
        }));
    }

    // 목록 필터용 상태 해석. 값이 없으면 진행중(홈의 기본 화면), ALL이면 상태 조건을 걸지 않는다.
    // ARCHIVED는 소프트 삭제에 해당하므로 명시적으로 요청해도 조회할 수 없다.
    /**
     * Uses the same membership, status filtering and fixed ordering as the public project list,
     * while exposing the period and write-state data required by internal tools.
     */
    @Transactional(readOnly = true)
    public Page<ProjectSearchResult> searchMyProjects(Long userId, String statusParam,
                                                       String keyword, Pageable pageable) {
        return findMyProjects(userId, statusParam, keyword, pageable)
                .map(row -> {
                    ProjectEntity project = row.project();
                    return new ProjectSearchResult(
                            project.getId(),
                            project.getName(),
                            project.getStatus(),
                            project.getStartDate(),
                            project.getTargetDate(),
                            project.getTargetBudget(),
                            ProjectPolicy.isOpenForContent(project),
                            // 조회 조건이 "내가 참여중"이라 멤버 행은 이미 조인돼 있다.
                            // 여기서 버리고 나중에 다시 찾으면 프로젝트 수만큼 쿼리가 더 나간다.
                            row.membership().getRole(),
                            row.membership().isOwner());
                });
    }

    private Page<MyProjectRow> findMyProjects(Long userId, String statusParam,
                                               String keyword, Pageable pageable) {
        ProjectStatus status = parseFilterStatus(statusParam);
        String searchKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        return projectRepository.findMyProjects(
                userId, ProjectMemberStatus.ACTIVE, ProjectStatus.ARCHIVED,
                status, searchKeyword,
                ProjectStatus.ONGOING, ProjectStatus.HOLDING,
                pageable);
    }

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

        // 4) 멤버 정보를 한 번에 조회 (userId → user). 루프 안에서 개별 조회하지 않는다.
        //    이름뿐 아니라 부서·직급까지 필요해 getNameMap이 아니라 getUserMap을 쓴다 —
        //    수정 화면의 참여자 카드가 "이름 · 부서"로 표시한다.
        Set<Long> memberUserIds = activeMembers.stream()
                .map(ProjectMemberEntity::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserEntity> userById = userService.getUserMap(memberUserIds);

        // 5) 마일스톤 (목표일 오름차순)
        List<MilestoneEntity> milestones = milestoneRepository.findByProjectIdOrderByTargetDateAscIdAsc(projectId);

        // 6) 조립 — progress는 오늘 날짜로 계산한 파생값
        UserEntity ownerUser = userById.get(ownerMember.getUserId());
        ProjectDetailResponse.Owner owner = ProjectDetailResponse.Owner.builder()
                .userId(ownerMember.getUserId())
                .name(ownerUser == null ? null : ownerUser.getName())
                .department(ownerUser == null ? null : ownerUser.getDepartment())
                .position(ownerUser == null ? null : ownerUser.getPosition())
                .status(ownerUser == null ? null : ownerUser.getStatus())
                .ownerRole(ownerMember.getRole())
                .build();

        List<ProjectDetailResponse.Member> memberDtos = participants.stream()
                .map(m -> {
                    UserEntity user = userById.get(m.getUserId());
                    return ProjectDetailResponse.Member.builder()
                            .userId(m.getUserId())
                            .name(user == null ? null : user.getName())
                            .department(user == null ? null : user.getDepartment())
                            .position(user == null ? null : user.getPosition())
                            .status(user == null ? null : user.getStatus())
                            .role(m.getRole())
                            .build();
                })
                .toList();

        List<MilestoneSummary> milestoneDtos = MilestoneSummary.listFrom(milestones);

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

        // 2) 상태 변경 권한 (PROJECT_017): 수정과 같은 기준 — 오너이거나 부서가 PM인 참여자.
        //    에러코드를 PROJECT_005와 나눠 두는 이유는 규칙이 달라서가 아니라 화면 문구가 달라야 하기 때문이다.
        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, userId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.NO_STATUS_CHANGE_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller, currentUserService.getCurrentUser(userId))) {
            throw BaseException.type(ProjectErrorCode.NO_STATUS_CHANGE_PERMISSION);
        }

        // 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단
        currentUserService.getEmployedUser(userId);

        // 3) 상태 값 파싱 (PROJECT_018)
        ProjectStatus target = parseStatus(statusStr);

        // 4) 전이 규칙 검증 (PROJECT_019): 종료 상태(COMPLETED/ARCHIVED)의 되돌림 차단
        if (!ProjectPolicy.isAllowedTransition(project.getStatus(), target)) {
            throw BaseException.type(ProjectErrorCode.STATUS_NOT_REVERTIBLE);
        }

        // 5) 상태 변경 (영속 엔티티 → dirty checking으로 UPDATE)
        project.changeStatus(target);

        // 6) 참여중 멤버 전원에게 알림. 탈퇴 멤버는 ACTIVE 조회로 빠지고, 변경자·퇴사자는 발행기가 걸러낸다.
        notificationPublisher.publish(NotificationType.PROJECT_STATUS_CHANGED,
                projectMemberService.getActiveMemberIds(projectId), userId, NotificationTarget.project(projectId),
                project.getName(), target.getDescription());

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
    private Long budgetOrZero(Long budget) {
        return budget == null ? 0L : budget;
    }

    // 마일스톤 목록 null 보정 + 목표일·목표 내용이 둘 다 비어 있는 빈 항목은 무시(제거).
    private List<MilestoneRequest> cleanMilestones(List<MilestoneRequest> milestones) {
        if (milestones == null) {
            return List.of();
        }
        List<MilestoneRequest> result = new ArrayList<>();
        for (MilestoneRequest m : milestones) {
            if (ProjectPolicy.isEmptyMilestone(m.targetDate(), m.goal())) {
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
            if (!ProjectPolicy.isCompleteMilestone(m.targetDate(), m.goal())) {
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
    private void updateParticipants(ProjectEntity project, Long actorId,
                                    Map<Long, MemberRequest> requested) {
        Long projectId = project.getId();
        Map<Long, ProjectMemberEntity> current = new LinkedHashMap<>();
        for (ProjectMemberEntity m : projectMemberRepository.findParticipants(projectId)) {
            current.put(m.getUserId(), m);
        }

        LocalDateTime now = LocalDateTime.now();
        List<ProjectMemberEntity> toInsert = new ArrayList<>();
        // 알림 수신자. 재참여(reactivate)도 당사자에겐 "추가"라 신규와 같은 목록에 담는다.
        List<Long> added = new ArrayList<>();
        List<Long> removed = new ArrayList<>();

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
                added.add(req.userId());
            } else if (existing.getStatus() == ProjectMemberStatus.LEFT) {
                existing.reactivate(req.role());
                added.add(req.userId());
            } else if (!Objects.equals(existing.getRole(), req.role())) {
                // 역할 변경은 알리지 않는다. 참여 여부가 바뀐 게 아니라 표기가 바뀐 것이다.
                existing.updateRole(req.role());
            }
        }

        // 요청에서 빠진 참여중(ACTIVE) 참여자: soft-delete (LEFT였던 사람은 그대로 둠)
        for (ProjectMemberEntity m : current.values()) {
            if (m.getStatus() == ProjectMemberStatus.ACTIVE && !requested.containsKey(m.getUserId())) {
                m.leave(now);
                removed.add(m.getUserId());
            }
        }

        if (!toInsert.isEmpty()) {
            projectMemberRepository.saveAll(toInsert);
        }
        // 기존 엔티티(reactivate/updateRole/leave)는 영속 상태라 flush 시 dirty checking으로 자동 UPDATE.

        // PM이 자기 자신을 참여자 목록에서 빼는 경우가 있어, 제외 알림도 행위자를 걸러야 한다(발행기가 처리).
        notificationPublisher.publish(NotificationType.PROJECT_MEMBER_ADDED,
                added, actorId, NotificationTarget.project(projectId), project.getName());

        // 제외 알림만 이동할 곳이 없다. 프로젝트 상세는 참여중 멤버만 볼 수 있어(MEMBER_001),
        // PROJECT로 보내면 방금 제외된 사람이 눌렀을 때 못 들어가는 곳으로 안내하게 된다.
        notificationPublisher.publish(NotificationType.PROJECT_MEMBER_REMOVED,
                removed, actorId, NotificationTarget.none(), project.getName());
    }

    // 마일스톤 동기화(diff) — 요청 목록을 최종 상태로 맞춘다. 참여자 diff(updateParticipants)와 같은 패턴.
    // 전체 교체가 아니라 diff여야 하는 이유: 마일스톤은 완료 시각(completed_at)을 갖는데,
    // 지웠다 다시 넣으면 한 건만 고쳐도 나머지 전부의 완료 기록이 사라진다.
    //
    // 현재 프로젝트의 마일스톤만 후보로 두므로, 다른 프로젝트의 id를 실어 보내도 자동으로 걸러진다.
    // (id는 테이블 전역으로 부여되어 남의 마일스톤 id도 유효한 값이다 → 소속 확인이 없으면 덮어쓸 수 있다)
    private void syncMilestones(Long projectId, List<MilestoneRequest> milestones) {
        Map<Long, MilestoneEntity> current = new LinkedHashMap<>();
        for (MilestoneEntity m : milestoneRepository.findByProjectId(projectId)) {
            current.put(m.getId(), m);
        }

        // 기존 항목(id 있음)의 내용을 먼저 모은다. 신규가 기존과 같은 내용이면 요청 순서와 무관하게 신규를 버리기 위함이다.
        // 이미 저장된 중복(과거 데이터)은 id로 식별되므로 이 집합 때문에 지워지지 않는다.
        Set<MilestoneKey> seen = new HashSet<>();
        for (MilestoneRequest m : milestones) {
            if (m.milestoneId() != null) {
                seen.add(new MilestoneKey(m.targetDate(), m.goal()));
            }
        }

        List<MilestoneEntity> toInsert = new ArrayList<>();
        Set<Long> kept = new LinkedHashSet<>();
        for (MilestoneRequest m : milestones) {
            // id 없음 = 신규. 목표일·내용이 완전히 같은 것이 이미 있으면 건너뛴다 —
            // 구별할 수 없는 마일스톤이 둘이면 완료 체크도 어느 쪽에 한 건지 알 수 없어진다.
            if (m.milestoneId() == null) {
                if (!seen.add(new MilestoneKey(m.targetDate(), m.goal()))) {
                    continue;
                }
                toInsert.add(MilestoneEntity.builder()
                        .projectId(projectId)
                        .targetDate(m.targetDate())
                        .goal(m.goal())
                        .build());
                continue;
            }
            // 같은 id가 두 번 오면 첫 것만 채택한다(참여자 중복 처리와 동일).
            if (!kept.add(m.milestoneId())) {
                continue;
            }
            // 이 프로젝트에 없는 id — 존재하지 않거나 다른 프로젝트의 것.
            // 신규로 만들어 주면 완료 상태가 사라진 것을 아무도 모르므로 명시적으로 거부한다.
            MilestoneEntity existing = current.get(m.milestoneId());
            if (existing == null) {
                throw BaseException.type(ProjectErrorCode.MILESTONE_NOT_FOUND);
            }
            // 완료 시각은 건드리지 않는다. 목표일을 미루거나 문구를 고쳐도 달성한 사실은 그대로다.
            existing.update(m.targetDate(), m.goal());
        }

        if (!toInsert.isEmpty()) {
            milestoneRepository.saveAll(toInsert);
        }

        // 요청에 없던 기존 마일스톤은 삭제(hard). 완료된 것도 함께 사라지므로
        // 수정 화면은 반드시 전체 목록을 실어 보내야 한다.
        List<MilestoneEntity> toDelete = current.values().stream()
                .filter(m -> !kept.contains(m.getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            milestoneRepository.deleteAll(toDelete);
        }
    }

    // 마일스톤 중복 판정용 값 키. 사용자에게 두 마일스톤은 목표일과 목표 내용이 같으면 같은 것이다.
    private record MilestoneKey(LocalDate targetDate, String goal) {
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
