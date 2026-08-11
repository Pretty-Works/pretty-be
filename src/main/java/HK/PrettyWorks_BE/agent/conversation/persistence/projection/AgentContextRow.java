package HK.PrettyWorks_BE.agent.conversation.persistence.projection;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;

// FastAPI에 넘길 대화 맥락(messages[]) JPQL 프로젝션 결과.
// LLM에 보내는 값이라 role·content만 있으면 되고, 나머지 컬럼은 토큰 낭비다.
public record AgentContextRow(
        AgentRole role,
        String content
) {
}
