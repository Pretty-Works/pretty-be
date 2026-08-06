package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentInteractionKind;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Schema(description = "APPROVAL이면 approvalId, QUESTION이면 questionId로 그대로 쓴다.", example = "41")
            Long interactionId,

            @Schema(description = "APPROVAL(승인 카드) · QUESTION(되묻기)", example = "APPROVAL")
            AgentInteractionKind kind,

            String label,

            // implementation = Object.class 를 빼면 JsonNode 게터가 전부 필드로 펼쳐진다(AgentMessageRequest 주석 참고).
            @Schema(implementation = Object.class,
                    description = "끊기기 전 화면에 떠 있던 카드 원문. SSE의 approval_request·question 페이로드와 같다.",
                    example = "{\"approvalId\": 41, \"tool\": \"task.create\", \"access\": \"WRITE\", "
                            + "\"summary\": \"할 일 2건 추가\", \"previewText\": \"· 첫 번째 / · 두 번째\"}")
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
