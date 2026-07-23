package HK.PrettyWorks_BE.calendar.schedule.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleListResponse(
        List<ScheduleItem> schedules
) {

    @Builder
    public record ScheduleItem(
            Long id,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String type,
            boolean isLeave,
            // 휴가일 때만 값이 있고, 일반 일정이면 null이라 응답에서 생략된다. (휴가 도메인 구현 시 채워짐)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String leaveType,
            Owner owner,
            List<Participant> participants
    ) {
    }

    @Builder
    public record Owner(
            Long userId,
            String name
    ) {
    }

    @Builder
    public record Participant(
            Long userId,
            String name,
            String role
    ) {
    }
}
