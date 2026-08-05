package HK.PrettyWorks_BE.agent.internal.service;

import HK.PrettyWorks_BE.agent.internal.dto.res.AgentTaskListResponse;
import HK.PrettyWorks_BE.global.util.WeekRange;
import HK.PrettyWorks_BE.task.dto.res.TaskWeeklyResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import HK.PrettyWorks_BE.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTaskToolServiceTest {

    private static final Long ME = 3L;

    private final TaskService taskService = mock(TaskService.class);
    private final UserService userService = mock(UserService.class);
    private final AgentTaskToolService service = new AgentTaskToolService(taskService, userService);

    @BeforeEach
    void stubNameLookup() {
        when(userService.getNameMap(anyCollection())).thenReturn(Map.of(ME, "정우진"));
    }

    // "밀렸다"는 개념은 미래 주에 성립하지 않는다. 다음 주 목록에 지난 주 미완료가 섞여 나오면
    // 에이전트가 "다음 주에 이게 마감이에요"라고 틀린 답을 만든다 — 저장은 없고 답변만 틀려서 눈에 안 띈다.
    @Test
    void dropsCarryOverWhenLookingAtAFutureWeek() {
        WeekRange nextWeek = WeekRange.of(LocalDate.now(), 1);
        when(taskService.getMyWeeklyTasks(eq(ME), any(WeekRange.class))).thenReturn(List.of(
                task(41L, "지난 주에 못 끝낸 것", nextWeek.start().minusDays(3)),
                task(58L, "다음 주 마감", nextWeek.start().plusDays(1))));

        AgentTaskListResponse result = service.list(ME, null, 1, false);

        assertThat(result.tasks()).extracting(AgentTaskListResponse.AgentTask::taskId)
                .containsExactly(58L);
        assertThat(result.summary().carryOverCount()).isZero();
        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    void keepsCarryOverForTheCurrentWeek() {
        WeekRange thisWeek = WeekRange.of(LocalDate.now(), 0);
        when(taskService.getMyWeeklyTasks(eq(ME), any(WeekRange.class))).thenReturn(List.of(
                task(41L, "지난 주에 못 끝낸 것", thisWeek.start().minusDays(3)),
                task(58L, "이번 주 마감", thisWeek.start().plusDays(1))));

        AgentTaskListResponse result = service.list(ME, null, 0, false);

        assertThat(result.tasks()).extracting(AgentTaskListResponse.AgentTask::taskId)
                .containsExactly(41L, 58L);
        assertThat(result.summary().carryOverCount()).isEqualTo(1);
        assertThat(result.scope()).isEqualTo("MINE");
    }

    // 필터를 걸면 집계도 같이 줄어야 한다. 서버가 준 요약을 그대로 실어 보내면
    // "2건 중 1건 완료"라고 답하면서 목록엔 1건만 보이는 상태가 된다.
    @Test
    void recountsTheSummaryAfterFiltering() {
        WeekRange thisWeek = WeekRange.of(LocalDate.now(), 0);
        when(taskService.getMyWeeklyTasks(eq(ME), any(WeekRange.class))).thenReturn(List.of(
                done(41L, "끝낸 것", thisWeek.start().plusDays(1)),
                task(58L, "남은 것", thisWeek.start().plusDays(2))));

        AgentTaskListResponse result = service.list(ME, null, 0, true);

        assertThat(result.tasks()).extracting(AgentTaskListResponse.AgentTask::taskId)
                .containsExactly(58L);
        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.summary().completed()).isZero();
        assertThat(result.summary().completionRate()).isZero();
    }

    private TaskWeeklyResponse task(Long taskId, String content, LocalDate dueDate) {
        return TaskWeeklyResponse.builder()
                .taskId(taskId)
                .content(content)
                .dueDate(dueDate)
                .completed(false)
                .projectId(7L)
                .projectName("에이전트 v2")
                .build();
    }

    private TaskWeeklyResponse done(Long taskId, String content, LocalDate dueDate) {
        return TaskWeeklyResponse.builder()
                .taskId(taskId)
                .content(content)
                .dueDate(dueDate)
                .completed(true)
                .projectId(7L)
                .projectName("에이전트 v2")
                .build();
    }
}
