package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;

public record AgentCancelResponse(
        String runId,
        AgentRunStatus status,
        boolean canceled
) {
}
