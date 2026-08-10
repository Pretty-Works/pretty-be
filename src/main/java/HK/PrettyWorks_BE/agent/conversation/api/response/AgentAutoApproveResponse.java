package HK.PrettyWorks_BE.agent.conversation.api.response;

public record AgentAutoApproveResponse(
        Long conversationId,
        boolean autoApprove
) {
}
