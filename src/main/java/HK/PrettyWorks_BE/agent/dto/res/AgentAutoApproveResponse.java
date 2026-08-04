package HK.PrettyWorks_BE.agent.dto.res;

public record AgentAutoApproveResponse(
        Long conversationId,
        boolean autoApprove
) {
}
