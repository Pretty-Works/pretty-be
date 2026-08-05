package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// milestone.list — 중간 목표와 달성 현황. "일정 위험해 보여?"의 가장 직접적인 근거다.
//
// isOverdue·isNext는 서버가 계산해 내려준다. 에이전트가 직접 날짜를 비교하면
// 오늘 기준이 틀어져 지연을 못 잡거나 없는 지연을 만들어낸다.
@Builder
public record AgentMilestoneListResponse(
        Long projectId,
        List<AgentMilestone> milestones,
        Summary summary,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentMilestone(
            Long milestoneId,
            String goal,
            LocalDate targetDate,
            boolean completed,
            // 목표일이 지났는데 미완료. 지연 판단의 직접 근거.
            boolean isOverdue,
            // 미완료 중 목표일이 가장 이른 하나만 true. 마일스톤은 순서대로 완료하는 것이 원칙이라
            // milestone.toggleStatus는 이 항목부터 처리해야 한다.
            boolean isNext
    ) {
    }

    @Builder
    public record Summary(
            int total,
            int completed,
            int completionRate,
            int overdueCount
    ) {
    }
}
