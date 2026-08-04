package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.constant.AgentInteractionKind;

import java.time.LocalDateTime;

public record AgentPendingInteractionRow(
        Long interactionId,
        AgentInteractionKind kind,
        String label,
        String payloadJson,
        String publicRunId,
        Long conversationId,
        String conversationTitle,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
