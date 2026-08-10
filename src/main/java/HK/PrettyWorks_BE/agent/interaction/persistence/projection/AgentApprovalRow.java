package HK.PrettyWorks_BE.agent.interaction.persistence.projection;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;

import java.time.LocalDateTime;

// 대화 상세의 지난 승인 카드. alternatives는 payloadJson 안에만 있어 원문을 함께 가져온다.
public record AgentApprovalRow(
        Long approvalId,
        Long runInternalId,
        AgentAccessType access,
        String label,
        String previewText,
        String payloadJson,
        AgentInteractionStatus status,
        String alternativeId,
        LocalDateTime resolvedAt
) {
}
