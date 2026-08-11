package HK.PrettyWorks_BE.agent.conversation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 읽음 처리 결과. 프론트가 목록을 다시 받지 않고도 그 줄의 표시를 끌 수 있게 확정된 지점을 돌려준다.
public record AgentConversationReadResponse(
        @Schema(example = "15")
        Long conversationId,

        @Schema(description = "읽음으로 확정된 메시지 id. 말풍선이 하나도 없으면 null입니다.",
                nullable = true, example = "102")
        Long lastReadMessageId
) {
}
