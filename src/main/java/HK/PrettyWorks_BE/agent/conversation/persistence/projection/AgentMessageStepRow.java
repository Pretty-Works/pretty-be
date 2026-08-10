package HK.PrettyWorks_BE.agent.conversation.persistence.projection;

// 대화 상세 조회에서 v2 step을 메시지별로 묶기 위한 가벼운 프로젝션.
public record AgentMessageStepRow(
        Long messageId,
        long seq,
        String payload
) {
}
