package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentInteractionKind;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record AgentPendingInteractionsResponse(
        int count,
        List<PendingInteraction> interactions
) {
    public AgentPendingInteractionsResponse {
        interactions = List.copyOf(interactions);
        count = interactions.size();
    }

    public record PendingInteraction(
            Long interactionId,
            AgentInteractionKind kind,
            String label,
            JsonNode payload,
            String runId,
            Long conversationId,
            String conversationTitle,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        public PendingInteraction {
            payload = payload == null ? null : payload.deepCopy();
        }

        @Override
        public JsonNode payload() {
            return payload == null ? null : payload.deepCopy();
        }
    }
}
