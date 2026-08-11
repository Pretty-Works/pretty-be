package HK.PrettyWorks_BE.agent.conversation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 삭제 결과. 200이면 그 대화는 목록에서 사라진 상태다(soft delete — 행은 남고 deleted_at 만 찍힌다).
public record AgentConversationDeleteResponse(
        @Schema(description = "삭제된 대화 id", example = "12")
        Long conversationId
) {
}
