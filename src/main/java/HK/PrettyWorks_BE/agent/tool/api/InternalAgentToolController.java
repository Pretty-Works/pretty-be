package HK.PrettyWorks_BE.agent.tool.api;

import HK.PrettyWorks_BE.agent.tool.calendar.AgentCalendarToolService;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveBalanceResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveListResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveUpdateRequest;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentScheduleListResponse;
import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentScheduleUpdateRequest;
import HK.PrettyWorks_BE.agent.tool.common.AgentWriteExecutor;
import HK.PrettyWorks_BE.agent.tool.common.AgentWriteResults;
import HK.PrettyWorks_BE.agent.tool.meeting.AgentMeetingToolService;
import HK.PrettyWorks_BE.agent.tool.meeting.dto.AgentMeetingCreateRequest;
import HK.PrettyWorks_BE.agent.tool.meeting.dto.AgentMeetingDetailResponse;
import HK.PrettyWorks_BE.agent.tool.meeting.dto.AgentMeetingListResponse;
import HK.PrettyWorks_BE.agent.tool.post.AgentPostToolService;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostCreateRequest;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostDetailResponse;
import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostListResponse;
import HK.PrettyWorks_BE.agent.tool.project.AgentProjectToolService;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentBudgetSummaryResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentExpenseCreateRequest;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentExpenseListResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentMemberListResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentMilestoneListResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentMilestoneStatusRequest;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentProjectSearchResponse;
import HK.PrettyWorks_BE.agent.tool.replan.AgentReplanToolService;
import HK.PrettyWorks_BE.agent.tool.replan.dto.AgentReplanApplyRequest;
import HK.PrettyWorks_BE.agent.tool.replan.dto.AgentReplanCreateRequest;
import HK.PrettyWorks_BE.agent.tool.security.AgentUser;
import HK.PrettyWorks_BE.agent.tool.task.AgentTaskToolService;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskCreateRequest;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskListResponse;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskStatusRequest;
import HK.PrettyWorks_BE.agent.tool.user.AgentUserToolService;
import HK.PrettyWorks_BE.agent.tool.user.dto.AgentMeResponse;
import HK.PrettyWorks_BE.agent.tool.user.dto.AgentUserSearchResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// FastAPI 전용 내부 도구 엔드포인트.
//
// 컨트롤러가 하는 일은 매핑·바인딩·위임뿐이다. API 키 검증, X-Run-Id→userId 역산, 재직 확인,
// 승인 토큰 검증·해시 대조·소진, BaseResponse 래핑은 전부 공용 인프라가 처리한다(공동 규격 §5-1).
//
// 쓰기 엔드포인트는 @RequestBody로 DTO를 먼저 바인딩하지 않는다. 승인 해시는 원본 바이트와
// 대조해야 하고, 바인딩이 먼저 일어나면 검증 전에 변환 예외가 나가 토큰 폐기 규칙이 갈린다.
// 바디 역직렬화·검증은 AgentWriteExecutor가 승인 확인 뒤에 수행한다.
@RestController
@RequiredArgsConstructor
public class InternalAgentToolController implements InternalAgentToolApi {

    private static final String TASK_CREATE = "task.create";
    private static final String TASK_TOGGLE_STATUS = "task.toggleStatus";
    private static final String MEETING_CREATE = "meeting.create";
    private static final String POST_CREATE = "post.create";
    private static final String EXPENSE_CREATE = "expense.create";
    private static final String SCHEDULE_CREATE = "schedule.create";
    private static final String SCHEDULE_UPDATE = "schedule.update";
    private static final String LEAVE_CREATE = "leave.create";
    private static final String LEAVE_UPDATE = "leave.update";
    private static final String MILESTONE_TOGGLE_STATUS = "milestone.toggleStatus";
    private static final String REPLAN_CREATE = "replan.create";
    private static final String REPLAN_APPLY = "replan.apply";

    private final AgentUserToolService userToolService;
    private final AgentProjectToolService projectToolService;
    private final AgentCalendarToolService calendarToolService;
    private final AgentTaskToolService taskToolService;
    private final AgentMeetingToolService meetingToolService;
    private final AgentPostToolService postToolService;
    private final AgentReplanToolService replanToolService;
    private final AgentWriteExecutor writeExecutor;

    // ================================= 조회 (승인 불필요) =================================

    @Override
    @GetMapping("/api/internal/agent/me")
    public ResponseEntity<AgentMeResponse> me(@AgentUser Long userId) {
        return ResponseEntity.ok(userToolService.me(userId));
    }

    @Override
    @GetMapping("/api/internal/agent/users")
    public ResponseEntity<AgentUserSearchResponse> searchUsers(
            @AgentUser Long userId,
            @RequestParam String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userToolService.search(userId, keyword, department, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects")
    public ResponseEntity<AgentProjectSearchResponse> searchProjects(
            @AgentUser Long userId,
            // 기본값을 여기 두지 않는다. 생략 시 ONGOING으로 좁히는 규칙은 ProjectService가 소유하며,
            // 복사본을 만들면 나중에 한쪽만 바뀐다. 전체는 status=ALL.
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectToolService.search(userId, status, keyword, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/members")
    public ResponseEntity<AgentMemberListResponse> members(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectToolService.members(userId, projectId, name, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/milestones")
    public ResponseEntity<AgentMilestoneListResponse> milestones(
            @AgentUser Long userId,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectToolService.milestones(userId, projectId));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/budget")
    public ResponseEntity<AgentBudgetSummaryResponse> budget(
            @AgentUser Long userId,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectToolService.budget(userId, projectId));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/expenses")
    public ResponseEntity<AgentExpenseListResponse> expenses(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean onlyMine,
            @RequestParam(defaultValue = "DATE_DESC") String sort,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectToolService.expenses(
                userId, projectId, from, to, category, onlyMine, sort, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/meetings")
    public ResponseEntity<AgentMeetingListResponse> meetings(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(meetingToolService.list(userId, projectId, title, from, to, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<AgentMeetingDetailResponse> meetingDetail(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingToolService.detail(userId, projectId, meetingId));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/posts")
    public ResponseEntity<AgentPostListResponse> posts(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postToolService.list(userId, projectId, title, priority, size));
    }

    @Override
    @GetMapping("/api/internal/agent/projects/{projectId}/posts/{postId}")
    public ResponseEntity<AgentPostDetailResponse> postDetail(
            @AgentUser Long userId,
            @PathVariable Long projectId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postToolService.detail(userId, projectId, postId));
    }

    @Override
    @GetMapping("/api/internal/agent/tasks")
    public ResponseEntity<AgentTaskListResponse> tasks(
            @AgentUser Long userId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int weekOffset,
            @RequestParam(defaultValue = "false") boolean onlyIncomplete) {
        return ResponseEntity.ok(taskToolService.list(userId, projectId, weekOffset, onlyIncomplete));
    }

    @Override
    @GetMapping("/api/internal/agent/schedules")
    public ResponseEntity<AgentScheduleListResponse> schedules(
            @AgentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(calendarToolService.listSchedules(userId, from, to, userIds, type));
    }

    @Override
    @GetMapping("/api/internal/agent/leaves")
    public ResponseEntity<AgentLeaveListResponse> leaves(
            @AgentUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) String leaveType) {
        return ResponseEntity.ok(calendarToolService.listLeaves(userId, from, to, userIds, leaveType));
    }

    @Override
    @GetMapping("/api/internal/agent/leaves/balance")
    public ResponseEntity<AgentLeaveBalanceResponse> leaveBalance(
            @AgentUser Long userId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(calendarToolService.balance(userId, year));
    }

    // ================================= 쓰기 (승인 토큰 필수) =================================

    @Override
    @PostMapping(value = "/api/internal/agent/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.TasksCreated> createTasks(@AgentUser Long userId) {
        // Do not bind a business DTO in the controller. The executor first compares the exact raw
        // body with the approved canonical params, then converts and validates the batch.
        return ResponseEntity.ok(writeExecutor.executeValidated(
                TASK_CREATE,
                Map.of(),
                AgentTaskCreateRequest.class,
                AgentWriteResults.TasksCreated.class,
                (request, ignoredLegacyIdempotencyKey) -> taskToolService.create(userId, request)));
    }

    @Override
    @PatchMapping(value = "/api/internal/agent/tasks/{taskId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.TaskStatusChanged> toggleTaskStatus(
            @AgentUser Long userId, @PathVariable Long taskId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                TASK_TOGGLE_STATUS,
                Map.of("taskId", taskId),
                AgentTaskStatusRequest.class,
                AgentWriteResults.TaskStatusChanged.class,
                (request, ignoredIdempotencyKey) -> taskToolService.toggleStatus(userId, request)));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/projects/{projectId}/meetings",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.MeetingCreated> createMeeting(
            @AgentUser Long userId, @PathVariable Long projectId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                MEETING_CREATE,
                Map.of("projectId", projectId),
                AgentMeetingCreateRequest.class,
                AgentWriteResults.MeetingCreated.class,
                (request, idempotencyKey) ->
                        meetingToolService.create(userId, request, idempotencyKey)));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/projects/{projectId}/posts",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.PostCreated> createPost(
            @AgentUser Long userId, @PathVariable Long projectId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                POST_CREATE,
                Map.of("projectId", projectId),
                AgentPostCreateRequest.class,
                AgentWriteResults.PostCreated.class,
                (request, idempotencyKey) ->
                        postToolService.create(userId, request, idempotencyKey)));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/projects/{projectId}/expenses",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.ExpenseCreated> createExpense(
            @AgentUser Long userId, @PathVariable Long projectId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                EXPENSE_CREATE,
                Map.of("projectId", projectId),
                AgentExpenseCreateRequest.class,
                AgentWriteResults.ExpenseCreated.class,
                (request, idempotencyKey) ->
                        projectToolService.createExpense(userId, request, idempotencyKey)));
    }

    @Override
    @PatchMapping(value = "/api/internal/agent/projects/{projectId}/milestones/{milestoneId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.MilestoneStatusChanged> toggleMilestoneStatus(
            @AgentUser Long userId, @PathVariable Long projectId, @PathVariable Long milestoneId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                MILESTONE_TOGGLE_STATUS,
                Map.of("projectId", projectId, "milestoneId", milestoneId),
                AgentMilestoneStatusRequest.class,
                AgentWriteResults.MilestoneStatusChanged.class,
                (request, ignoredIdempotencyKey) ->
                        projectToolService.toggleMilestone(userId, request)));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/schedules", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.ScheduleCreated> createSchedule(@AgentUser Long userId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                SCHEDULE_CREATE,
                Map.of(),
                ScheduleCreateRequest.class,
                AgentWriteResults.ScheduleCreated.class,
                (request, idempotencyKey) ->
                        calendarToolService.createSchedule(userId, request, idempotencyKey)));
    }

    @Override
    @PatchMapping(value = "/api/internal/agent/schedules/{scheduleId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.ScheduleUpdated> updateSchedule(
            @AgentUser Long userId, @PathVariable Long scheduleId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                SCHEDULE_UPDATE,
                Map.of("scheduleId", scheduleId),
                AgentScheduleUpdateRequest.class,
                AgentWriteResults.ScheduleUpdated.class,
                (request, ignoredIdempotencyKey) ->
                        calendarToolService.updateSchedule(userId, request)));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/leaves", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.LeaveSaved> createLeave(@AgentUser Long userId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                LEAVE_CREATE,
                Map.of(),
                LeaveCreateRequest.class,
                AgentWriteResults.LeaveSaved.class,
                (request, idempotencyKey) ->
                        calendarToolService.createLeave(userId, request, idempotencyKey)));
    }

    @Override
    @PatchMapping(value = "/api/internal/agent/leaves/{leaveId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.LeaveSaved> updateLeave(
            @AgentUser Long userId, @PathVariable Long leaveId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                LEAVE_UPDATE,
                Map.of("leaveId", leaveId),
                AgentLeaveUpdateRequest.class,
                AgentWriteResults.LeaveSaved.class,
                (request, ignoredIdempotencyKey) ->
                        calendarToolService.updateLeave(userId, request)));
    }

    // 저장 단계도 승인을 받는다. 프로젝트 데이터는 아직 바뀌지 않지만, 사용자가 어떤 계획이 만들어졌는지
    // 여기서 한 번 보게 된다 — 에이전트가 외부 텍스트를 읽고 만든 계획이라 그 확인이 필요하다.
    @Override
    @PostMapping(value = "/api/internal/agent/projects/{projectId}/replans",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.ReplanCreated> createReplan(
            @AgentUser Long userId, @PathVariable Long projectId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                REPLAN_CREATE,
                Map.of("projectId", projectId),
                AgentReplanCreateRequest.class,
                AgentWriteResults.ReplanCreated.class,
                (request, ignoredIdempotencyKey) -> replanToolService.create(userId, request)));
    }

    // 재계획 적용은 여러 도메인을 한 번에 바꾼다. 반드시 이 호출 하나로 끝나야 한다 —
    // AgentWriteExecutor가 호출 한 번을 트랜잭션 하나로 감싸므로, 도구를 나눠 부르면
    // 앞의 변경만 커밋되고 뒤가 실패한 상태가 남는다.
    @Override
    @PostMapping(value = "/api/internal/agent/projects/{projectId}/replans/{replanId}/apply",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentWriteResults.ReplanApplied> applyReplan(
            @AgentUser Long userId, @PathVariable Long projectId, @PathVariable Long replanId) {
        return ResponseEntity.ok(writeExecutor.executeValidated(
                REPLAN_APPLY,
                Map.of("projectId", projectId, "replanId", replanId),
                AgentReplanApplyRequest.class,
                AgentWriteResults.ReplanApplied.class,
                (request, ignoredIdempotencyKey) -> replanToolService.apply(userId, request)));
    }
}
