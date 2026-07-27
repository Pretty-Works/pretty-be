package HK.PrettyWorks_BE.calendar.leave.dto.res;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record LeaveUpdateResponse(
        Long leaveId,
        Long scheduleId,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String reason
) {
}
