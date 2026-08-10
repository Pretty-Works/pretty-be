package HK.PrettyWorks_BE.agent.tool.user.dto;

import lombok.Builder;

// run.user — runId로 그 실행의 주인이 누구인지만 알려준다.
//
// AgentMeResponse와 나눈 이유: 저쪽은 LLM에게 문맥(부서·직책·오늘 날짜)을 주는 도구 응답이고,
// 이쪽은 BE 도구를 쓰지 않는 외부 MCP 서버(gmail-mcp 등)가 사용자별 credential을 찾기 위한
// 신원 조회다. 계약이 섞이면 /me의 필드 하나를 고칠 때 MCP까지 깨진다.
@Builder
public record AgentRunUserResponse(
        Long userId
) {
}
