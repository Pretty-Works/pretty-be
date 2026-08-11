package HK.PrettyWorks_BE.agent.conversation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AgentAutoApproveRequest(
        @Schema(description = "true면 이 대화에서는 승인 카드를 띄우지 않고 바로 실행한다.", example = "true")
        @NotNull Boolean autoApprove
) {
}
