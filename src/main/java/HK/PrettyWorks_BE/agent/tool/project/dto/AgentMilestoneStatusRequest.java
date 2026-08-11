package HK.PrettyWorks_BE.agent.tool.project.dto;

import jakarta.validation.constraints.NotNull;

// milestone.toggleStatus 요청. 토글이지만 목표 상태를 명시해 재시도가 상태를 뒤집지 않게 한다.
public record AgentMilestoneStatusRequest(
        @NotNull Long projectId,
        @NotNull Long milestoneId,
        @NotNull Boolean completed
) {
}
