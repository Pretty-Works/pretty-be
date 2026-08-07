package HK.PrettyWorks_BE.task.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.Percent;
import HK.PrettyWorks_BE.global.util.WeekRange;
import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.event.NotificationPublisher;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.req.TaskStatusRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse.TaskGroup;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse.TaskItem;
import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskStatusResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskWeeklyResponse;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.policy.TaskPolicy;
import HK.PrettyWorks_BE.task.repository.TaskHomeRow;
import HK.PrettyWorks_BE.task.repository.TaskProjectRow;
import HK.PrettyWorks_BE.task.repository.TaskRepository;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final CurrentUserService currentUserService;
    private final NotificationPublisher notificationPublisher;

    // 홈 '내 할 일'에 올릴 프로젝트 상태. 완료·중단·보관 프로젝트의 할 일은 더 손댈 일이 없어 뺀다.
    private static final List<ProjectStatus> HOME_PROJECT_STATUSES =
            List.of(ProjectStatus.ONGOING, ProjectStatus.HOLDING);

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        Long projectId = request.projectId();

        // 프로젝트 할 일이면 존재·멤버·상태·마감일 검증, 개인 할 일(null)이면 통과
        validateProjectForWrite(projectId, userId, request.dueDate());

        // 작성자 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단.
        // validateProjectForWrite 밖에 두어야 개인 할 일(projectId=null)도 함께 걸린다.
        currentUserService.getEmployedUser(userId);

        // 담당자 결정 — 비우면 본인, 남을 지정하면 배정 권한과 대상 멤버십을 검증한다.
        Long assigneeId = resolveAssignee(projectId, userId, request.assigneeId());

        // 완료는 미완료(completedAt=null)로 시작, projectId는 nullable
        TaskEntity task = TaskEntity.builder()
                .projectId(projectId)
                .assigneeId(assigneeId)
                .creatorId(userId)
                .content(request.content())
                .dueDate(request.dueDate())
                .build();
        taskRepository.save(task);

        publishAssigned(task);

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    /**
     * Creates one approved agent batch atomically. Every item is validated before the first
     * INSERT, so an invalid item cannot leave a partially-created batch behind.
     */
    @Transactional
    public List<TaskResponse> createBatch(Long userId, List<TaskRequest> requests) {
        if (requests == null || requests.isEmpty()
                || requests.size() > TaskPolicy.MAX_CREATE_BATCH_SIZE) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        // Keep the domain entry point safe even when it is called without the internal filter.
        currentUserService.getEmployedUser(userId);

        // A meeting often yields several action items for one project. Authorize that project
        // once, but still check every individual due date against the project period.
        Map<Long, ProjectEntity> projects = new LinkedHashMap<>();
        for (TaskRequest request : requests) {
            Long projectId = request.projectId();
            if (projectId == null) {
                continue;
            }
            ProjectEntity project = projects.computeIfAbsent(projectId,
                    id -> validatedWritableProject(id, userId));
            if (!ProjectPolicy.isWithinPeriod(project, request.dueDate())) {
                throw BaseException.type(TaskErrorCode.DUE_DATE_OUT_OF_RANGE);
            }
        }

        // 담당자도 INSERT 전에 전부 확정한다. 뒤늦게 거부되면 이미 저장된 항목이 남는다.
        List<Long> assignees = requests.stream()
                .map(request -> resolveAssignee(request.projectId(), userId, request.assigneeId()))
                .toList();

        List<TaskEntity> tasks = IntStream.range(0, requests.size())
                .mapToObj(i -> TaskEntity.builder()
                        .projectId(requests.get(i).projectId())
                        .assigneeId(assignees.get(i))
                        .creatorId(userId)
                        .content(requests.get(i).content())
                        .dueDate(requests.get(i).dueDate())
                        .build())
                .toList();
        taskRepository.saveAll(tasks);

        tasks.forEach(this::publishAssigned);

        return tasks.stream()
                .map(task -> TaskResponse.builder().taskId(task.getId()).build())
                .toList();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 담당자 또는 작성자만 수정 (TASK_004)
        if (!TaskPolicy.canModify(task, userId)) {
            throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) 호출자 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단
        currentUserService.getEmployedUser(userId);

        // 4) projectId 있으면 존재·멤버·상태·마감일 재검증, null이면 개인 할 일로 전환
        Long projectId = request.projectId();
        validateProjectForWrite(projectId, userId, request.dueDate());

        // 5) 개인 할 일로 전환하려면 담당자가 본인이어야 한다 (TASK_010).
        //    담당자가 남인 채로 프로젝트를 떼면 권한 판정 근거(멤버십)도 알림 대상도 사라진다.
        //    재배정을 지원하지 않으므로 이 경우 사용자는 삭제 후 다시 만들어야 한다.
        if (projectId == null && !task.isSelfAssigned()) {
            throw BaseException.type(TaskErrorCode.PERSONAL_TASK_NOT_ASSIGNABLE);
        }

        // 6) 갱신 (dirty checking으로 바뀐 컬럼만 UPDATE). 담당자는 이 API로 바꾸지 않는다.
        LocalDate previousDueDate = task.getDueDate();
        task.update(request.content(), projectId, request.dueDate());

        // 7) 마감일이 실제로 바뀐 경우만 담당자에게 알린다. 내용 오타 수정에도 알림이 가면 시끄럽다.
        //    본인이 자기 할 일을 고친 경우는 발행기가 행위자를 걸러 자동으로 빠진다.
        if (!previousDueDate.equals(task.getDueDate()) && task.getProjectId() != null) {
            notificationPublisher.publish(NotificationType.TASK_DUE_DATE_CHANGED,
                    List.of(task.getAssigneeId()), userId,
                    NotificationTargetType.PROJECT, task.getProjectId(),
                    task.getContent(), task.getDueDate());
        }

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    @Transactional
    public TaskResponse delete(Long userId, Long taskId) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자만 삭제 (TASK_005). 담당자에게 열어 주면 "하기 싫으면 삭제"가 된다.
        if (!TaskPolicy.canDelete(task, userId)) {
            throw BaseException.type(TaskErrorCode.NO_DELETE_PERMISSION);
        }

        // 3) 호출자 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단
        currentUserService.getEmployedUser(userId);

        // 4) 담당자에게 알린다. 삭제 뒤에는 엔티티 값을 읽을 수 없으니 미리 발행한다
        //    (같은 트랜잭션이라 커밋은 함께 이뤄진다). 본인 것을 지운 경우는 발행기가 걸러낸다.
        if (task.getProjectId() != null) {
            notificationPublisher.publish(NotificationType.TASK_DELETED,
                    List.of(task.getAssigneeId()), userId,
                    NotificationTargetType.PROJECT, task.getProjectId(), task.getContent());
        }

        // 5) hard delete (참조 자식 테이블 없어 안전)
        taskRepository.delete(task);

        return TaskResponse.builder()
                .taskId(taskId)
                .build();
    }

    @Transactional
    public TaskStatusResponse toggleStatus(Long userId, Long taskId, TaskStatusRequest request) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 담당자만 (TASK_004). 작성자가 남의 일을 완료 처리하면 완료율이 실제 진척과 어긋난다.
        if (!TaskPolicy.canToggle(task, userId)) {
            throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) 호출자 재직 검증 (USER_003) — 휴직자는 통과, 퇴사자만 차단
        currentUserService.getEmployedUser(userId);

        // 4) 완료 토글 (미완료→완료일 때만 시각 기록 = 멱등), dirty checking으로 UPDATE
        boolean wasCompleted = task.getCompletedAt() != null;
        task.toggleDone(request.done(), LocalDateTime.now());
        boolean nowCompleted = task.getCompletedAt() != null;

        return TaskStatusResponse.builder()
                .taskId(task.getId())
                .content(task.getContent())
                .completed(nowCompleted)
                .completedAt(task.getCompletedAt())
                .changed(wasCompleted != nowCompleted)
                .build();
    }

    // ====================== 재계획(replan) 진입점 ======================
    //
    // 재계획은 "내가 만든 할 일을 다시 배치하는" 일이라 권한을 새로 정하지 않는다.
    // 마감일 조정은 update, 삭제는 delete, 생성은 createBatch — 화면이 쓰는 공개 메서드를 그대로 쓴다.
    // 그쪽 규칙(수정은 담당자·작성자, 삭제는 작성자)이 곧 재계획이 손댈 수 있는 범위가 된다.
    //
    // 담당자 교체도 마찬가지다. 화면이 지원하지 않으므로 재계획도 하지 않는다 —
    // 넘길 일이 있으면 화면과 똑같이 지우고 새로 만든다(TASK_DELETE + TASK_CREATE).
    // 한쪽만 할 수 있게 두면 같은 조작의 결과가 경로에 따라 달라진다.
    //
    // 그래서 여기 남는 것은 조회 하나뿐이다. 쓰기는 전부 공개 메서드가 처리한다.

    /**
     * 재계획이 손댈 할 일을 소속·권한 검증을 마친 뒤 돌려준다.
     *
     * <p>taskId는 테이블 전역으로 부여되어 다른 프로젝트의 id도 유효한 값이다. 화면 경로는 담당자·작성자
     * 판정이 남의 프로젝트 할 일을 자연히 걸러내지만, 여기는 값을 '읽는' 단계라 소속을 직접 확인해야 한다.
     *
     * <p>같은 트랜잭션에서 이어지는 update·delete 호출은 여기서 적재된 엔티티를 그대로 쓰므로 조회가 늘지 않는다.
     */
    @Transactional(readOnly = true)
    public List<TaskEntity> loadEditableTasks(Long actorId, Long projectId, Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        if (taskIds.size() > TaskPolicy.MAX_REPLAN_BATCH_SIZE) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        projectMemberService.validateAccess(projectId, actorId);

        Set<Long> distinctIds = Set.copyOf(taskIds);
        List<TaskEntity> tasks = taskRepository.findAllById(distinctIds);
        if (tasks.size() != distinctIds.size()) {
            throw BaseException.type(TaskErrorCode.TASK_NOT_FOUND);
        }

        for (TaskEntity task : tasks) {
            // 존재하지 않는 id와 남의 프로젝트 id를 같은 코드로 응답한다 — 구분하면 존재 여부가 새어나간다.
            if (!projectId.equals(task.getProjectId())) {
                throw BaseException.type(TaskErrorCode.TASK_NOT_FOUND);
            }
            // 화면 수정과 같은 기준(TaskPolicy). 삭제는 여기에 더해 작성자를 요구하며 delete가 다시 판정한다.
            if (!TaskPolicy.canModify(task, actorId)) {
                throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
            }
        }
        return tasks;
    }

    @Transactional(readOnly = true)
    public TaskHomeResponse getTaskHome(Long userId) {
        // 완료 3일 이내 경계 + dDay 계산용 오늘
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        LocalDate today = LocalDate.now();

        // 미완료 or 완료 3일 이내, 진행중·보류 프로젝트만. 정렬: project_id(널 먼저), due_date
        List<TaskHomeRow> rows = taskRepository.findTaskHomeRows(userId, threshold, HOME_PROJECT_STATUSES);

        // projectId로 그룹핑 (정렬 순서 유지 → LinkedHashMap)
        Map<Long, List<TaskHomeRow>> byProject = new LinkedHashMap<>();
        for (TaskHomeRow r : rows) {
            byProject.computeIfAbsent(r.projectId(), k -> new ArrayList<>()).add(r);
        }

        // 실제 프로젝트 그룹 먼저, 개인(projectId=null) 그룹은 마지막
        List<TaskGroup> groups = new ArrayList<>();
        List<TaskHomeRow> personal = null;
        for (Map.Entry<Long, List<TaskHomeRow>> e : byProject.entrySet()) {
            if (e.getKey() == null) {
                personal = e.getValue();
                continue;
            }
            groups.add(toGroup(e.getKey(), e.getValue(), today, userId));
        }
        if (personal != null) {
            groups.add(toGroup(null, personal, today, userId));
        }

        return new TaskHomeResponse(groups);
    }

    // TaskHomeRow 묶음을 한 그룹으로 변환. done(completedAt≠null)·dDay(오늘~마감)는 여기서 파생.
    private TaskGroup toGroup(Long projectId, List<TaskHomeRow> rows, LocalDate today, Long userId) {
        String projectName = rows.get(0).projectName();       // 그룹 내 동일 (개인은 null)
        ProjectStatus status = rows.get(0).projectStatus();   // 그룹 내 동일 (개인은 null)
        List<TaskItem> items = rows.stream()
                .map(r -> new TaskItem(
                        r.taskId(),
                        r.content(),
                        r.completedAt() != null,
                        r.dueDate(),
                        ChronoUnit.DAYS.between(today, r.dueDate()),
                        // 홈은 내가 담당한 것만 나오므로 수정·토글은 항상 가능하다. 삭제만 작성자 전용이다.
                        r.creatorId().equals(userId)
                ))
                .toList();
        return new TaskGroup(projectId, projectName, status, items);
    }

    // 본인 담당 할 일의 주간 조회. 홈 조회(getTaskHome)는 "완료 3일 이내"라는 다른 경계를 쓰므로
    // 주 단위로 묻는 쪽(에이전트 task.list)은 이 진입점을 쓴다.
    // 주 범위 계산은 WeekRange가 소유한다 — 월요일의 정의가 여기서 갈리면 화면과 답변이 다른 주를 가리킨다.
    @Transactional(readOnly = true)
    public List<TaskWeeklyResponse> getMyWeeklyTasks(Long userId, WeekRange week) {
        // 지연분은 이번 주를 볼 때만 함께 준다. 다음 주 질문에 지난 지연이 섞이면
        // "다음 주에 할 일"을 알 수 없고, 지난 주 질문에는 그보다 더 오래된 것까지 딸려온다.
        LocalDate carryOverBefore = week.contains(LocalDate.now()) ? week.start() : null;

        return taskRepository.findMyWeeklyRows(
                        userId, week.start(), week.end(), carryOverBefore, ProjectStatus.ARCHIVED)
                .stream()
                .map(TaskWeeklyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskProjectResponse getTaskProject(Long userId, Long projectId, int weekOffset) {
        // 1) 프로젝트 존재·멤버십 검증 (PROJECT_004 / MEMBER_001) — 조회는 상태·마감일 검증 없이 멤버십만
        validateProjectAccess(projectId, userId);

        // 2) 조회자 부서 (isMine 판정용)
        DepartmentType viewerTeam = currentUserService.getCurrentUser(userId).getDepartment();

        // 3) 주 범위: 오늘이 속한 주의 월요일 + weekOffset주, 월~일(7일).
        //    월요일의 정의는 WeekRange가 소유한다 — 여기서 따로 계산하면 개인 주간 조회와 갈릴 수 있다.
        LocalDate today = LocalDate.now();
        WeekRange week = WeekRange.of(today, weekOffset);
        LocalDate weekStart = week.start();
        LocalDate weekEnd = week.end();

        // 4) rows: 그 주 마감분, dueDate 오름차순.
        //    지연된 미완료는 이번 주(weekOffset=0)에서만 함께 보여준다 — 다음 주 보드에 계속 따라붙으면
        //    "다음 주에 할 일"을 볼 수 없고, 지난 주 보드는 그 주의 기록이 아니게 된다.
        LocalDate carryOverBefore = (weekOffset == 0) ? weekStart : null;
        List<TaskProjectRow> rows =
                taskRepository.findTaskProjectRows(projectId, weekStart, weekEnd, carryOverBefore);

        // 5) 팀(담당자 부서)별 그룹핑 — 팀 내부는 쿼리의 dueDate 순서 유지
        Map<DepartmentType, List<TaskProjectRow>> byTeam = new LinkedHashMap<>();
        for (TaskProjectRow r : rows) {
            byTeam.computeIfAbsent(r.team(), k -> new ArrayList<>()).add(r);
        }

        // 6) 팀 정렬: 내 팀 먼저 → 나머지는 한글 설명 가나다순. summary.teams·groups에 동일 적용.
        Comparator<DepartmentType> teamOrder = Comparator
                .comparingInt((DepartmentType t) -> t == viewerTeam ? 0 : 1)
                .thenComparing(DepartmentType::getDescription);
        List<DepartmentType> orderedTeams = byTeam.keySet().stream().sorted(teamOrder).toList();

        // 7) groups + summary.teams 동시 빌드, 전체 집계 누적
        List<TaskProjectResponse.TeamGroup> groups = new ArrayList<>();
        List<TaskProjectResponse.TeamRate> teamRates = new ArrayList<>();
        int totalAll = 0;
        int doneAll = 0;
        for (DepartmentType team : orderedTeams) {
            List<TaskProjectRow> teamRows = byTeam.get(team);
            int teamTotal = teamRows.size();
            int teamDone = (int) teamRows.stream().filter(r -> r.completedAt() != null).count();
            totalAll += teamTotal;
            doneAll += teamDone;

            teamRates.add(new TaskProjectResponse.TeamRate(team, teamDone, teamTotal, Percent.floorRate(teamDone, teamTotal)));

            List<TaskProjectResponse.TaskItem> items = teamRows.stream()
                    .map(r -> toItem(r, today, userId))
                    .toList();
            groups.add(new TaskProjectResponse.TeamGroup(team, team == viewerTeam, items));
        }

        // 8) 전체 요약 (rate: 내림, total=0이면 0)
        TaskProjectResponse.Summary summary =
                new TaskProjectResponse.Summary(totalAll, doneAll, Percent.floorRate(doneAll, totalAll), teamRates);

        // 프로젝트명은 행마다 같은 값이라 아무 행에서 꺼내면 된다. 행이 없으면 null.
        String projectName = rows.isEmpty() ? null : rows.get(0).projectName();

        return new TaskProjectResponse(weekStart, weekEnd, projectName, summary, groups);
    }

    // TaskProjectRow → TaskItem. done(completedAt≠null)·dDay(오늘~마감, 내림)·overdue(미완료 && 마감<오늘) 파생.
    private TaskProjectResponse.TaskItem toItem(TaskProjectRow r, LocalDate today, Long userId) {
        boolean isAssignee = r.assigneeId().equals(userId);
        boolean isCreator = r.creatorId().equals(userId);

        return new TaskProjectResponse.TaskItem(
                r.taskId(),
                r.content(),
                new TaskProjectResponse.Assignee(r.assigneeId(), r.assigneeName()),
                r.completedAt() != null,
                r.dueDate(),
                ChronoUnit.DAYS.between(today, r.dueDate()),
                r.completedAt() == null && r.dueDate().isBefore(today),
                // TaskPolicy와 같은 규칙 — 수정은 둘 다, 토글은 담당자, 삭제는 작성자.
                isAssignee || isCreator,
                isAssignee,
                isCreator
        );
    }

    // 쓰기(생성/수정)용: 프로젝트 존재(PROJECT_004)·멤버(MEMBER_001)·상태(완료/보관 불가, PROJECT_020)·마감일 기간(TASK_007) 검증.
    // null이면 개인 할 일이라 통과. 마감일은 프로젝트 기간 양끝(start·target 당일) 포함.
    // 담당자 확정. 비었거나 본인이면 그대로 본인이고, 남을 지정한 경우만 검증이 붙는다.
    //
    // 배정 권한을 TaskPolicy에 두지 않은 이유: 프로젝트 멤버십 조회가 필요한데
    // TaskPolicy는 DB를 쓰지 않는 순수 판정 클래스다. 프로젝트 권한이므로 ProjectPolicy를 그대로 쓴다.
    private Long resolveAssignee(Long projectId, Long creatorId, Long requestedAssigneeId) {
        if (requestedAssigneeId == null || requestedAssigneeId.equals(creatorId)) {
            return creatorId;
        }
        // 개인 할 일은 배정할 상대가 없다. 프로젝트가 없으면 배정 권한을 판정할 근거도 없다.
        if (projectId == null) {
            throw BaseException.type(TaskErrorCode.PERSONAL_TASK_NOT_ASSIGNABLE);
        }

        ProjectMemberEntity caller = projectMemberService.getActiveMembership(projectId, creatorId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.NO_ASSIGN_PERMISSION));
        if (!ProjectPolicy.canUpdate(caller, currentUserService.getCurrentUser(creatorId))) {
            throw BaseException.type(TaskErrorCode.NO_ASSIGN_PERMISSION);
        }
        // 참여자가 아닌 사람에게 배정하면 그는 볼 수도 없는 할 일을 받는다.
        if (projectMemberService.getActiveMembership(projectId, requestedAssigneeId).isEmpty()) {
            throw BaseException.type(TaskErrorCode.ASSIGNEE_NOT_MEMBER);
        }

        return requestedAssigneeId;
    }

    // 남에게 배정한 경우에만 알린다. 본인 할 일은 발행기가 행위자를 걸러 자동으로 빠지지만,
    // 개인 할 일은 targetId로 쓸 projectId가 없어 여기서 미리 끊는다.
    private void publishAssigned(TaskEntity task) {
        if (task.getProjectId() == null || task.isSelfAssigned()) {
            return;
        }

        notificationPublisher.publish(NotificationType.TASK_ASSIGNED,
                List.of(task.getAssigneeId()), task.getCreatorId(),
                NotificationTargetType.PROJECT, task.getProjectId(),
                task.getContent(), task.getDueDate());
    }

    private void validateProjectForWrite(Long projectId, Long userId, LocalDate dueDate) {
        if (projectId == null) {
            return;
        }
        ProjectEntity project = validatedWritableProject(projectId, userId);
        if (!ProjectPolicy.isWithinPeriod(project, dueDate)) {
            throw BaseException.type(TaskErrorCode.DUE_DATE_OUT_OF_RANGE);
        }
    }

    private ProjectEntity validatedWritableProject(Long projectId, Long userId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectMemberService.validateActiveMember(projectId, userId);
        if (!ProjectPolicy.isOpenForContent(project)) {
            throw BaseException.type(ProjectErrorCode.PROJECT_CLOSED);
        }
        return project;
    }

    // 읽기(조회)용: projectId가 있으면 프로젝트 존재(PROJECT_004)·작성자 멤버(MEMBER_001)만 검증. null이면 개인 할 일이라 통과.
    private void validateProjectAccess(Long projectId, Long userId) {
        if (projectId == null) {
            return;
        }
        projectMemberService.validateAccess(projectId, userId);
    }
}
