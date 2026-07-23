package HK.PrettyWorks_BE.calendar.schedule.dto.res;

import lombok.Builder;

@Builder
public record ScheduleUpdateResponse(
        Long scheduleId
) {
}
