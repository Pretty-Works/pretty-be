package HK.PrettyWorks_BE.replan.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import HK.PrettyWorks_BE.replan.constant.ReplanOperationType;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.domain.ReplanEntity;
import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import HK.PrettyWorks_BE.replan.exception.ReplanErrorCode;
import HK.PrettyWorks_BE.replan.repository.ReplanRepository;
import HK.PrettyWorks_BE.task.domain.TaskEntity;
import HK.PrettyWorks_BE.task.exception.TaskErrorCode;
import HK.PrettyWorks_BE.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 재계획이 스스로 책임지는 것만 검증한다. 기간·권한 같은 도메인 규칙은 ProjectService·TaskService의 몫이라
// 여기서 다시 확인하면 규칙이 두 벌이 되고, 그쪽이 바뀔 때 이 테스트가 애먼 이유로 깨진다.
class ReplanApplyServiceTest {

    private static final Long ACTOR = 3L;
    private static final Long PROJECT = 7L;
    private static final Long REPLAN = 123L;

    private final ReplanRepository replanRepository = mock(ReplanRepository.class);
    private final ReplanService replanService = mock(ReplanService.class);
    private final ReplanAccessGuard accessGuard = mock(ReplanAccessGuard.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final TaskService taskService = mock(TaskService.class);

    private final ReplanApplyService service = new ReplanApplyService(
            replanRepository, replanService, accessGuard, projectService, taskService);

    /**
     * 계획을 세운 뒤 누가 먼저 값을 바꿨다면 절반도 반영해서는 안 된다.
     * 트랜잭션이 롤백해 주더라도, 검증을 실행 뒤로 미루면 그때까지의 알림은 이미 나간 뒤다.
     */
    @Test
    void conflictStopsBeforeAnythingIsChanged() {
        givenReplan();
        // 계획 당시 마감일은 8/22였는데 지금은 8/25 — 그 사이 누가 먼저 옮겼다.
        givenProjectDetail(LocalDate.of(2026, 8, 25));
        givenOperations(Map.of(ReplanOperationType.PROJECT_TARGET_DATE_CHANGE,
                List.of(dateChange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28)))));

        assertThatThrownBy(() -> service.apply(ACTOR, PROJECT, REPLAN, ReplanScenarioType.EXTEND))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ReplanErrorCode.SCENARIO_CONFLICT));

        verify(projectService, never()).update(anyLong(), anyLong(), anyLong(), any());
    }

    /**
     * 이 기능의 핵심 가정이다. 할 일 마감일은 프로젝트 기간 안이어야 하므로(TASK_007)
     * 기간을 먼저 넓히지 않으면 논리적으로 옳은 계획이 검증에서 거부된다.
     * 에이전트가 준 배열 순서를 그대로 실행하면 안 되는 이유이기도 하다.
     */
    @Test
    void projectPeriodIsExtendedBeforeTaskDueDates() {
        givenReplan();
        givenProjectDetail(LocalDate.of(2026, 8, 22));
        TaskEntity task = givenTask(101L, ACTOR, ACTOR, "API 명세 검토", LocalDate.of(2026, 8, 20));
        givenOperations(Map.of(
                ReplanOperationType.PROJECT_TARGET_DATE_CHANGE,
                List.of(dateChange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28))),
                ReplanOperationType.TASK_DUE_DATE_CHANGE,
                List.of(taskDateChange(101L, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 26)))));

        var result = service.apply(ACTOR, PROJECT, REPLAN, ReplanScenarioType.EXTEND);

        InOrder order = inOrder(projectService, taskService);
        order.verify(projectService).update(anyLong(), anyLong(), anyLong(), any());
        order.verify(taskService).update(anyLong(), anyLong(), any());

        assertThat(result.taskDueDateChangedCount()).isEqualTo(1);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));   // 실제 변경은 TaskService의 몫
    }

    /**
     * 삭제는 작성자만 할 수 있는데(TASK_005), 대상을 적재하는 기준은 그보다 넓은 '수정 권한'이다.
     * 그 차이를 실행 단계에서 만나면 앞선 변경을 전부 돌린 뒤 롤백된다.
     */
    @Test
    void deletePermissionIsCheckedBeforeAnythingIsChanged() {
        givenReplan();
        givenProjectDetail(LocalDate.of(2026, 8, 22));
        // 담당자는 본인이지만 만든 사람은 남이다 — 수정은 되고 삭제는 안 된다.
        givenTask(103L, ACTOR, 99L, "구버전 마이그레이션", LocalDate.of(2026, 8, 20));
        givenOperations(Map.of(
                ReplanOperationType.PROJECT_TARGET_DATE_CHANGE,
                List.of(dateChange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 28))),
                ReplanOperationType.TASK_DELETE,
                List.of(ReplanOperation.builder()
                        .operation(ReplanOperationType.TASK_DELETE)
                        .taskId(103L)
                        .expectedContent("구버전 마이그레이션")
                        .build())));

        assertThatThrownBy(() -> service.apply(ACTOR, PROJECT, REPLAN, ReplanScenarioType.REDUCE_SCOPE))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(TaskErrorCode.NO_DELETE_PERMISSION));

        verify(projectService, never()).update(anyLong(), anyLong(), anyLong(), any());
        verify(taskService, never()).delete(anyLong(), anyLong());
    }

    // ================================= 준비 =================================

    private void givenReplan() {
        ReplanEntity replan = ReplanEntity.builder()
                .projectId(PROJECT)
                .createdBy(ACTOR)
                .build();
        when(replanRepository.findById(REPLAN)).thenReturn(Optional.of(replan));
    }

    private void givenOperations(Map<ReplanOperationType, List<ReplanOperation>> operations) {
        when(replanService.loadOperations(anyLong(), anyLong(), any())).thenReturn(operations);
    }

    private void givenProjectDetail(LocalDate endDate) {
        when(projectService.getDetail(ACTOR, PROJECT)).thenReturn(ProjectDetailResponse.builder()
                .projectId(PROJECT)
                .version(1L)
                .name("에이전트 v2")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(endDate)
                .budget(0L)
                .status(ProjectStatus.ONGOING)
                .owner(ProjectDetailResponse.Owner.builder()
                        .userId(ACTOR).name("김피엠").ownerRole("PM").build())
                .members(List.of())
                .milestones(List.of())
                .build());
    }

    // id는 저장 시점에 DB가 붙이는 값이라 빌더로는 넣을 수 없다. 재계획은 taskId로 대상을 찾으므로 테스트에선 채워 준다.
    private TaskEntity givenTask(Long taskId, Long assigneeId, Long creatorId, String content, LocalDate dueDate) {
        TaskEntity task = TaskEntity.builder()
                .projectId(PROJECT)
                .assigneeId(assigneeId)
                .creatorId(creatorId)
                .content(content)
                .dueDate(dueDate)
                .build();
        ReflectionTestUtils.setField(task, "id", taskId);
        when(taskService.loadEditableTasks(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(task));
        return task;
    }

    private ReplanOperation dateChange(LocalDate from, LocalDate to) {
        return ReplanOperation.builder()
                .operation(ReplanOperationType.PROJECT_TARGET_DATE_CHANGE)
                .from(from)
                .to(to)
                .build();
    }

    private ReplanOperation taskDateChange(Long taskId, LocalDate from, LocalDate to) {
        return ReplanOperation.builder()
                .operation(ReplanOperationType.TASK_DUE_DATE_CHANGE)
                .taskId(taskId)
                .from(from)
                .to(to)
                .build();
    }
}
