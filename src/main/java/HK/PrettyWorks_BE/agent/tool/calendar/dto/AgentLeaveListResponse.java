package HK.PrettyWorks_BE.agent.tool.calendar.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// leave.list — 기간 내 휴가 내역.
//
// schedule.list에도 휴가가 type=LEAVE로 보이지만 사유·유형·일수는 여기에만 있다.
// 휴가 자체를 다뤄야 하면(수정할 leaveId를 잡거나, 팀원 휴가와 겹치는지 보려면) 이 도구를 쓴다.
@Builder
public record AgentLeaveListResponse(
        List<AgentLeave> leaves,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentLeave(
            Long leaveId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            Integer days,
            // 사유는 본인 것만 내려간다. 남의 휴가 사유는 민감 정보라 답변에 섞이면 안 된다.
            // 캘린더 화면은 전원에게 공개하지만, 에이전트는 답변에 그대로 옮겨 적을 수 있어 여기서만 막는다.
            String reason,
            Long userId,
            String userName,
            boolean canEdit
    ) {
    }
}
