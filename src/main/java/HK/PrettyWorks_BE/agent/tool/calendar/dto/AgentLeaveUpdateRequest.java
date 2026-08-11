package HK.PrettyWorks_BE.agent.tool.calendar.dto;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveUpdateRequest;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// leave.update 요청. leaveId를 뺀 모든 필드가 선택이고 미전달(null)이면 기존 값이 유지된다.
// reason만 예외 — null은 "유지", 빈 문자열("")은 "사유 비우기"다.
public record AgentLeaveUpdateRequest(
        @NotNull Long leaveId,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        String reason
) {
    public LeaveUpdateRequest toDomain() {
        return LeaveUpdateRequest.builder()
                .leaveType(leaveType)
                .startDate(startDate)
                .endDate(endDate)
                .reason(reason)
                .build();
    }
}
