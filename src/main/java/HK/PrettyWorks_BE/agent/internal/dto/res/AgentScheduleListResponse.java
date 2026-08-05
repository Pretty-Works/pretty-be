package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// schedule.list — 기간·사용자별 일정. 일정 생성 전 겹침 확인의 근거가 된다.
//
// 서버는 시간 겹침을 막지 않는다. 겹침 판단은 에이전트의 일이고, 이 응답이 그 판단의 유일한 재료다.
@Builder
public record AgentScheduleListResponse(
        LocalDate from,
        LocalDate to,
        List<AgentSchedule> schedules,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentSchedule(
            Long scheduleId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String type,
            // 요청자가 작성자면 true (SCHEDULE_003). 휴가 일정은 isLeave도 함께 볼 것 — canEdit이 true여도
            // schedule.update로는 못 고치고 leave.update를 써야 한다 (SCHEDULE_007).
            boolean canEdit,
            List<String> participantNames,
            boolean isLeave
    ) {
    }
}
