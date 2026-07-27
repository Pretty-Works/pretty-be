package HK.PrettyWorks_BE.task.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.req.TaskStatusRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse.TaskGroup;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse.TaskItem;
import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.repository.TaskHomeRow;
import HK.PrettyWorks_BE.task.repository.TaskProjectRow;
import HK.PrettyWorks_BE.task.repository.TaskRepository;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final UserRepository userRepository;

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        Long projectId = request.projectId();

        // 프로젝트 할 일이면 존재·멤버·상태·마감일 검증, 개인 할 일(null)이면 통과
        validateProjectForWrite(projectId, userId, request.dueDate());

        // 담당자 = 작성자 본인(userId), 완료는 미완료(completedAt=null)로 시작, projectId는 nullable
        TaskEntity task = TaskEntity.builder()
                .projectId(projectId)
                .assigneeId(userId)
                .content(request.content())
                .dueDate(request.dueDate())
                .build();
        taskRepository.save(task);

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자 본인만 수정 (TASK_004) — self-only라 assigneeId가 곧 작성자
        if (!task.getAssigneeId().equals(userId)) {
            throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) projectId 있으면 존재·멤버·상태·마감일 재검증, null이면 개인 할 일로 전환
        Long projectId = request.projectId();
        validateProjectForWrite(projectId, userId, request.dueDate());

        // 4) 갱신 (dirty checking으로 바뀐 컬럼만 UPDATE)
        task.update(request.content(), projectId, request.dueDate());

        return TaskResponse.builder()
                .taskId(task.getId())
                .build();
    }

    @Transactional
    public TaskResponse delete(Long userId, Long taskId) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자 본인만 삭제 (TASK_005) — self-only라 assigneeId가 곧 작성자
        if (!task.getAssigneeId().equals(userId)) {
            throw BaseException.type(TaskErrorCode.NO_DELETE_PERMISSION);
        }

        // 3) hard delete (참조 자식 테이블 없어 안전)
        taskRepository.delete(task);

        return TaskResponse.builder()
                .taskId(taskId)
                .build();
    }

    @Transactional
    public void toggleStatus(Long userId, Long taskId, TaskStatusRequest request) {
        // 1) 대상 할 일 조회 (TASK_003)
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.TASK_NOT_FOUND));

        // 2) 작성자 본인만 (TASK_004) — 완료 토글도 수정과 동일 권한
        if (!task.getAssigneeId().equals(userId)) {
            throw BaseException.type(TaskErrorCode.NO_EDIT_PERMISSION);
        }

        // 3) 완료 토글 (미완료→완료일 때만 시각 기록 = 멱등), dirty checking으로 UPDATE
        task.toggleDone(request.done(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public TaskHomeResponse getTaskHome(Long userId) {
        // 완료 3일 이내 경계 + dDay 계산용 오늘
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        LocalDate today = LocalDate.now();

        // 미완료 or 완료 3일 이내, ARCHIVED 프로젝트 제외. 정렬: project_id(널 먼저), due_date
        List<TaskHomeRow> rows = taskRepository.findTaskHomeRows(userId, threshold, ProjectStatus.ARCHIVED);

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
            groups.add(toGroup(e.getKey(), e.getValue(), today));
        }
        if (personal != null) {
            groups.add(toGroup(null, personal, today));
        }

        return new TaskHomeResponse(groups);
    }

    // TaskHomeRow 묶음을 한 그룹으로 변환. done(completedAt≠null)·dDay(오늘~마감)는 여기서 파생.
    private TaskGroup toGroup(Long projectId, List<TaskHomeRow> rows, LocalDate today) {
        String projectName = rows.get(0).projectName();   // 그룹 내 동일 (개인은 null)
        List<TaskItem> items = rows.stream()
                .map(r -> new TaskItem(
                        r.taskId(),
                        r.content(),
                        r.completedAt() != null,
                        r.dueDate(),
                        ChronoUnit.DAYS.between(today, r.dueDate())
                ))
                .toList();
        return new TaskGroup(projectId, projectName, items);
    }

    @Transactional(readOnly = true)
    public TaskProjectResponse getTaskProject(Long userId, Long projectId, int weekOffset) {
        // 1) 프로젝트 존재·멤버십 검증 (TASK_001 / MEMBER_001) — 조회는 상태·마감일 검증 없이 멤버십만
        validateProjectAccess(projectId, userId);

        // 2) 조회자 부서 (isMine 판정용). 토큰은 유효한데 유저가 없으면 인증을 신뢰할 수 없어 UNAUTHORIZED.
        DepartmentType viewerTeam = userRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED))
                .getDepartment();

        // 3) 주 범위: 오늘이 속한 주의 월요일 + weekOffset주, 월~일(7일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 4) rows: 이번 주 범위 + 지난 주 이전 미완료 carry-over, dueDate 오름차순
        List<TaskProjectRow> rows = taskRepository.findTaskProjectRows(projectId, weekStart, weekEnd);

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

            teamRates.add(new TaskProjectResponse.TeamRate(team, teamDone, teamTotal, rate(teamDone, teamTotal)));

            List<TaskProjectResponse.TaskItem> items = teamRows.stream()
                    .map(r -> toItem(r, today))
                    .toList();
            groups.add(new TaskProjectResponse.TeamGroup(team, team == viewerTeam, items));
        }

        // 8) 전체 요약 (rate: 내림, total=0이면 0)
        TaskProjectResponse.Summary summary =
                new TaskProjectResponse.Summary(totalAll, doneAll, rate(doneAll, totalAll), teamRates);

        return new TaskProjectResponse(weekStart, weekEnd, summary, groups);
    }

    // TaskProjectRow → TaskItem. done(completedAt≠null)·dDay(오늘~마감, 내림)·overdue(미완료 && 마감<오늘) 파생.
    private TaskProjectResponse.TaskItem toItem(TaskProjectRow r, LocalDate today) {
        return new TaskProjectResponse.TaskItem(
                r.taskId(),
                r.content(),
                new TaskProjectResponse.Assignee(r.assigneeId(), r.assigneeName()),
                r.completedAt() != null,
                r.dueDate(),
                ChronoUnit.DAYS.between(today, r.dueDate()),
                r.completedAt() == null && r.dueDate().isBefore(today)
        );
    }

    // 완료율(%) 내림. total=0이면 0으로 가드.
    private int rate(int done, int total) {
        return total == 0 ? 0 : done * 100 / total;
    }

    // 쓰기(생성/수정)용: 프로젝트 존재(TASK_001)·멤버(MEMBER_001)·상태(완료/보관 불가, TASK_006)·마감일 기간(TASK_007) 검증.
    // null이면 개인 할 일이라 통과. 마감일은 프로젝트 기간 양끝(start·target 당일) 포함.
    private void validateProjectForWrite(Long projectId, Long userId, LocalDate dueDate) {
        if (projectId == null) {
            return;
        }
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BaseException.type(TaskErrorCode.PROJECT_NOT_FOUND));
        projectMemberService.validateActiveMember(projectId, userId);
        ProjectStatus status = project.getStatus();
        if (status == ProjectStatus.COMPLETED || status == ProjectStatus.ARCHIVED) {
            throw BaseException.type(TaskErrorCode.PROJECT_CLOSED);
        }
        if (dueDate.isBefore(project.getStartDate()) || dueDate.isAfter(project.getTargetDate())) {
            throw BaseException.type(TaskErrorCode.DUE_DATE_OUT_OF_RANGE);
        }
    }

    // 읽기(조회)용: projectId가 있으면 프로젝트 존재(TASK_001)·작성자 멤버(MEMBER_001)만 검증. null이면 개인 할 일이라 통과.
    private void validateProjectAccess(Long projectId, Long userId) {
        if (projectId == null) {
            return;
        }
        if (!projectRepository.existsById(projectId)) {
            throw BaseException.type(TaskErrorCode.PROJECT_NOT_FOUND);
        }
        projectMemberService.validateActiveMember(projectId, userId);
    }
}
