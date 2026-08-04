package HK.PrettyWorks_BE.agent.dto.req;

import jakarta.validation.constraints.NotNull;

public record AgentAutoApproveRequest(
        @NotNull Boolean autoApprove
) {
}
