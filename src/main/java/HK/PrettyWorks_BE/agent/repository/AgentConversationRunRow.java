package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;

// 대화 목록 화면이 쓰는 실행 요약. 대화마다 가장 최근 실행 한 건만 골라 쓴다.
public record AgentConversationRunRow(
        Long conversationId,
        Long runInternalId,
        String runId,
        AgentRunStatus status
) {
}
