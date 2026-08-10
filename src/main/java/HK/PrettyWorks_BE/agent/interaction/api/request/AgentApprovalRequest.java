package HK.PrettyWorks_BE.agent.interaction.api.request;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentApprovalRequest(
        @Schema(description = "APPROVED(승인) · REJECTED(거절) · ALTERNATIVE(대안 선택)", example = "APPROVED")
        @NotNull AgentDecision decision,

        @Schema(description = "ALTERNATIVE일 때 고른 대안 id. approval_request 이벤트의 alternatives[].id를 그대로 보낸다.",
                nullable = true, example = "FILL_FORM")
        @Size(max = 40) String alternativeId,

        @Schema(description = "거절 사유(선택, 최대 200자). 에이전트가 다음 판단에 참고한다.",
                nullable = true, example = "금액이 잘못됐어요")
        @Size(max = 200) String reason
) {
}
