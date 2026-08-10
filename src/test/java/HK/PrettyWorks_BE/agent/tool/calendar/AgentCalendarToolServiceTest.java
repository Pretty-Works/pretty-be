package HK.PrettyWorks_BE.agent.tool.calendar;

import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveListResponse;
import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveListResponse;
import HK.PrettyWorks_BE.calendar.leave.service.LeaveService;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCalendarToolServiceTest {

    private static final Long ME = 3L;
    private static final Long SOMEONE_ELSE = 9L;

    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final LeaveService leaveService = mock(LeaveService.class);
    private final AgentCalendarToolService service =
            new AgentCalendarToolService(scheduleService, leaveService);

    // 캘린더 화면은 남의 휴가 사유까지 전원에게 보여준다(팀 결정). 하지만 에이전트는 받은 값을
    // 그대로 답변에 옮겨 적으므로, 여기서 막지 않으면 남의 병가 사유가 대화에 섞인다.
    @Test
    void masksSomeoneElsesLeaveReason() {
        when(leaveService.getLeaves(any(), any(), anyList(), eq(null), any(Pageable.class)))
                .thenReturn(List.of(
                        leave(21L, ME, "개인 사정"),
                        leave(22L, SOMEONE_ELSE, "병원 진료")));

        AgentLeaveListResponse result = service.listLeaves(
                ME, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of(ME, SOMEONE_ELSE), null);

        AgentLeaveListResponse.AgentLeave mine = result.leaves().get(0);
        AgentLeaveListResponse.AgentLeave theirs = result.leaves().get(1);

        assertThat(mine.reason()).isEqualTo("개인 사정");
        assertThat(mine.canEdit()).isTrue();
        assertThat(theirs.reason()).isNull();
        assertThat(theirs.canEdit()).isFalse();
        // 사유만 가리고 나머지는 남긴다 — 누가 언제 쉬는지는 알아야 겹치는 날을 피할 수 있다.
        assertThat(theirs.userName()).isEqualTo("동료");
        assertThat(theirs.startDate()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    // 휴가 명세는 기간 오류도 REQUEST_001이다. 일정 서비스에 검증을 맡기면 SCHEDULE_004가 나간다.
    @Test
    void reportsLeavePeriodErrorsWithTheLeaveContract() {
        assertThatThrownBy(() -> service.listLeaves(
                ME, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), null, null))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
    }

    @Test
    void rejectsARangeWiderThanAYear() {
        assertThatThrownBy(() -> service.listLeaves(
                ME, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31), null, null))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getCode()).isEqualTo(GlobalErrorCode.VALIDATION_ERROR));
    }

    private LeaveListResponse leave(Long leaveId, Long userId, String reason) {
        return LeaveListResponse.builder()
                .leaveId(leaveId)
                .scheduleId(leaveId + 100)
                .leaveType(LeaveType.ANNUAL)
                .startDate(LocalDate.of(2026, 8, 13))
                .endDate(LocalDate.of(2026, 8, 14))
                .days(2)
                .reason(reason)
                .userId(userId)
                .userName(userId.equals(ME) ? "나" : "동료")
                .build();
    }
}
