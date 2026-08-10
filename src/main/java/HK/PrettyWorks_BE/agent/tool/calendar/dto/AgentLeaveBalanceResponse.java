package HK.PrettyWorks_BE.agent.tool.calendar.dto;

import lombok.Builder;

// leave.balance — 본인 연차 부여·사용·잔여.
//
// 서버가 잔여 초과를 막지 않으므로 remainingDays는 음수가 될 수 있다.
// 휴가 신청 전에 이 도구를 먼저 부르고, 초과하면 승인 카드에 적어야 한다.
// 화면용 응답의 tenureYears(근속연수)는 뺐다 — 에이전트가 쓸 일이 없다.
@Builder
public record AgentLeaveBalanceResponse(
        int year,
        int grantedDays,
        int usedDays,
        int remainingDays
) {
}
