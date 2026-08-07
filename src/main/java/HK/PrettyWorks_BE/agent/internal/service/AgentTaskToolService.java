package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.internal.dto.req.AgentTaskCreateRequest;
import HK.PrettyWorks_BE.agent.internal.dto.req.AgentTaskStatusRequest;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentTaskListResponse;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentTaskListResponse.AgentTask;
import HK.PrettyWorks_BE.agent.internal.dto.res.AgentWriteResults;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.global.util.Percent;
import HK.PrettyWorks_BE.global.util.WeekRange;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskStatusResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskWeeklyResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

// task.list — 개인·프로젝트 두 모드를 한 도구로 합친다.
// 공개 API는 둘로 나뉘어 있지만 에이전트 입장에선 "할 일을 본다"는 하나의 의도다.
@Service
@RequiredArgsConstructor
public class AgentTaskToolService {

    // 너무 먼 주차를 훑는 질의를 막는다. 앞뒤 두 달이면 "지난 달에 뭐 했지"까지 답할 수 있다.
    private static final int MAX_WEEK_OFFSET = 8;

    private static final String SCOPE_MINE = "MINE";
    private static final String SCOPE_PROJECT = "PROJECT";

    private final TaskService taskService;
    private final UserService userService;

    public AgentTaskListResponse list(Long userId, Long projectId, int weekOffset, boolean onlyIncomplete) {
        if (weekOffset < -MAX_WEEK_OFFSET || weekOffset > MAX_WEEK_OFFSET) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
        WeekRange week = WeekRange.of(LocalDate.now(), weekOffset);

        List<AgentTask> tasks = projectId == null
                ? mineTasks(userId, week)
                : projectTasks(userId, projectId, weekOffset, week);

        // "밀렸다"는 개념은 미래 주에 성립하지 않는다. 다음 주 목록에 지난 주 미완료가 섞여 나오면
        // 에이전트가 "다음 주에 이게 마감이에요"라고 틀린 답을 만든다.
        if (weekOffset > 0) {
            tasks = tasks.stream().filter(task -> !task.isCarryOver()).toList();
        }
        if (onlyIncomplete) {
            tasks = tasks.stream().filter(task -> !task.completed()).toList();
        }

        return AgentTaskListResponse.builder()
                .weekStart(week.start())
                .weekEnd(week.end())
                .scope(projectId == null ? SCOPE_MINE : SCOPE_PROJECT)
                .tasks(tasks)
                .summary(summarize(tasks))
                .totalCount(tasks.size())
                // 주 단위 + 프로젝트 단위라 건수가 자연히 제한된다. 별도 상한을 두지 않는다.
                .truncated(false)
                .build();
    }

    // ================================= 쓰기 =================================

    public AgentWriteResults.TasksCreated create(Long userId, AgentTaskCreateRequest request) {
        List<TaskRequest> requests = request.tasks();
        // 건수 상한·기간·권한 검증과 전부-또는-전무 저장은 TaskService가 담당한다.
        List<TaskResponse> created = taskService.createBatch(userId, requests);

        // createBatch는 요청 순서를 유지한 채 saveAll 하므로 순서로 짝지을 수 있다.
        // 저장된 값을 다시 읽지 않는 대신, 방금 검증을 통과한 요청값을 그대로 쓴다.
        List<AgentWriteResults.TasksCreated.CreatedTask> tasks = IntStream.range(0, created.size())
                .mapToObj(index -> {
                    TaskRequest source = requests.get(index);
                    return AgentWriteResults.TasksCreated.CreatedTask.builder()
                            .taskId(created.get(index).taskId())
                            .content(source.content())
                            .dueDate(source.dueDate())
                            .projectId(source.projectId())
                            .build();
                })
                .toList();

        return AgentWriteResults.TasksCreated.builder()
                .createdCount(tasks.size())
                .tasks(tasks)
                .build();
    }

    public AgentWriteResults.TaskStatusChanged toggleStatus(Long userId, AgentTaskStatusRequest request) {
        TaskStatusResponse changed =
                taskService.toggleStatus(userId, request.taskId(), request.toDomain());

        return AgentWriteResults.TaskStatusChanged.builder()
                .taskId(changed.taskId())
                .content(changed.content())
                .completed(changed.completed())
                .completedAt(changed.completedAt())
                // false면 이미 그 상태였다는 뜻. 에러가 아니라 "바뀐 게 없다"고 답하면 된다.
                .changed(changed.changed())
                .build();
    }

    // ================================= 내부 헬퍼 =================================

    private List<AgentTask> mineTasks(Long userId, WeekRange week) {
        List<TaskWeeklyResponse> rows = taskService.getMyWeeklyTasks(userId, week);
        if (rows.isEmpty()) {
            return List.of();
        }
        // 담당자는 전부 요청자 본인이라 이름을 한 번만 찾으면 된다.
        String myName = userService.getNameMap(Set.of(userId)).get(userId);

        return rows.stream()
                .map(row -> AgentTask.builder()
                        .taskId(row.taskId())
                        .content(row.content())
                        .dueDate(row.dueDate())
                        .completed(row.completed())
                        .isCarryOver(row.dueDate().isBefore(week.start()))
                        // 이 조회는 담당자가 본인인 할 일만 가져온다(findMyWeeklyRows).
                        // 담당자는 수정도 토글도 되므로 둘 다 true다.
                        .canEdit(true)
                        .canToggle(true)
                        .assigneeId(userId)
                        .assigneeName(myName)
                        .projectId(row.projectId())
                        .projectName(row.projectName())
                        .build())
                .toList();
    }

    private List<AgentTask> projectTasks(Long userId, Long projectId, int weekOffset, WeekRange week) {
        // 프로젝트 존재·참여 검증(PROJECT_004 / MEMBER_001)은 이 호출이 담당한다.
        TaskProjectResponse source = taskService.getTaskProject(userId, projectId, weekOffset);

        // 화면은 팀별로 묶어 보여주지만 에이전트에겐 평면 목록이 낫다 —
        // 부서 구분은 답변에 쓰이지 않고 중첩만 깊어진다.
        return source.groups().stream()
                .flatMap(group -> group.tasks().stream())
                .map(task -> AgentTask.builder()
                        .taskId(task.taskId())
                        .content(task.content())
                        .dueDate(task.dueDate())
                        .completed(task.done())
                        .isCarryOver(task.dueDate().isBefore(week.start()))
                        // 서버가 TaskPolicy로 판정한 값을 그대로 넘긴다. 여기서 다시 계산하면
                        // 규칙이 두 벌이 되고, 어긋나는 순간 에이전트는 할 수 있는 일을 못 한다고
                        // 답하거나 못 하는 일을 제안했다가 저장 단계에서 거절당한다.
                        .canEdit(task.canEdit())
                        .canToggle(task.canToggle())
                        .assigneeId(task.assignee().userId())
                        .assigneeName(task.assignee().name())
                        .projectId(projectId)
                        .projectName(source.projectName())
                        .build())
                .sorted((left, right) -> left.dueDate().compareTo(right.dueDate()))
                .toList();
    }

    // 집계는 필터를 적용한 뒤의 목록에서 낸다. 서버가 준 summary를 그대로 쓰면
    // onlyIncomplete·미래 주 carry-over 제거가 반영되지 않아 목록과 숫자가 어긋난다.
    private AgentTaskListResponse.Summary summarize(List<AgentTask> tasks) {
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(AgentTask::completed).count();
        int carryOver = (int) tasks.stream().filter(AgentTask::isCarryOver).count();

        return AgentTaskListResponse.Summary.builder()
                .total(total)
                .completed(completed)
                .completionRate(Percent.floorRate(completed, total))
                .carryOverCount(carryOver)
                .build();
    }
}
