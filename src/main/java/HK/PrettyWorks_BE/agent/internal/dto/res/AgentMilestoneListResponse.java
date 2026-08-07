package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// milestone.list — 중간 목표와 달성 현황. "일정 위험해 보여?"의 가장 직접적인 근거다.
//
// 날짜 비교(isOverdue)와 순서 판정(toggleable)을 서버가 끝내서 내려준다. 에이전트가 직접
// 계산하면 오늘 기준이 틀어져 지연을 못 잡거나, 순서 규칙을 몰라 거절당할 변경을 제안한다.
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
            // 미완료 중 목표일이 가장 이른 하나만 true. 다음에 완료할 대상을 고르는 데 쓴다.
            boolean isNext,
            // 지금 이 마일스톤의 완료 상태를 바꿀 수 있는지. 서버(MilestonePolicy)가 순서를 보고 판정한다.
            //
            // isNext로 대신할 수 없다. isNext는 미완료 항목에만 붙어서 "완료 취소가 되는지"를
            // 표현하지 못한다 — 완료된 건 전부 isNext=false라, 취소를 제안했다가 PROJECT_024로 거절당한다.
            // ⚠️ 순서만 본 값이라 권한(오너·PM)은 포함하지 않는다. 그건 project.search로 따로 확인할 것.
            boolean toggleable
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
