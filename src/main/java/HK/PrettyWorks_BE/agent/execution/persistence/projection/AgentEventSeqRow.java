package HK.PrettyWorks_BE.agent.execution.persistence.projection;

// 승인 카드에 붙일 seq를 찾기 위한 이벤트 조각.
// 이벤트와 카드를 잇는 FK가 없어 payload 안의 approvalId로 맞춘다.
public record AgentEventSeqRow(
        long seq,
        String payload
) {
}
