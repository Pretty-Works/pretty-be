package HK.PrettyWorks_BE.calendar.leave.dto.res;

import lombok.Builder;

@Builder
public record LeaveCreateResponse(
        Long leaveId
) {
}
