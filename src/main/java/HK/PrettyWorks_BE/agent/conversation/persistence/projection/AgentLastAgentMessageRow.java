package HK.PrettyWorks_BE.agent.conversation.persistence.projection;

// 대화 목록의 "안 읽음" 판정에 쓰는 값. 대화마다 마지막 AGENT 메시지 id 하나만 필요하다.
// USER 메시지를 빼는 이유는 AgentConversationEntity.isUnread 주석 참고.
public record AgentLastAgentMessageRow(
        Long conversationId,
        Long lastAgentMessageId
) {
}
