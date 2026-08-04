package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.constant.AgentRole;

import java.time.LocalDateTime;

// 스레드 상세(말풍선 목록) JPQL 프로젝션 결과.
// rawJson(LONGTEXT)을 일부러 빼서 목록 조회가 무거워지지 않게 한다.
public record AgentMessageRow(
        Long messageId,
        Long runId,
        AgentRole role,
        String content,
        Boolean success,
        String actionType,
        String actionJson,
        LocalDateTime createdAt
) {
}
