package HK.PrettyWorks_BE.agent.execution.api.response;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;

public record AgentCancelResponse(
        String runId,
        AgentRunStatus status,
        boolean canceled
) {
}
