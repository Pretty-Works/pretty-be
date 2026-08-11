package HK.PrettyWorks_BE.agent.interaction.persistence.projection;

// 대화 목록의 '확인 필요' 배지. 실행마다 대기 중인 승인 카드 id를 담는다.
public record AgentPendingApprovalRow(
        Long runInternalId,
        Long approvalId
) {
}
