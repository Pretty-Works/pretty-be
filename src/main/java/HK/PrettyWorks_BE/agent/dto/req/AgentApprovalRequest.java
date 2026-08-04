package HK.PrettyWorks_BE.agent.dto.req;

import HK.PrettyWorks_BE.agent.constant.AgentDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentApprovalRequest(
        @NotNull AgentDecision decision,
        @Size(max = 40) String alternativeId,
        @Size(max = 200) String reason
) {
}
