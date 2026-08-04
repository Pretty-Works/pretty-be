package HK.PrettyWorks_BE.agent.client.dto;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

// 에이전트 서버(FastAPI)로 보내는 요청. 화면용 DTO와 성격이 달라 client 패키지에 둔다.
// 이 구조는 LLM팀과의 계약이므로 바꾸려면 양 팀 합의가 필요하다.
@Builder
public record AgentGoalRequest(

        // 프론트가 보내지 않고 서버가 JWT에서 꺼내 넣는 값.
        Long userId,

        Long conversationId,

        // 사용자 입력 원문.
        String goal,

        // 이전 대화 맥락. 서버가 DB에서 최근 N건을 꺼내 오래된 순으로 채운다.
        // 실패한 AGENT 메시지는 제외한다 — "연결하지 못했습니다" 같은 안내가 맥락에 섞이면 안 된다.
        List<ContextMessage> messages,

        // 프론트가 올려보낸 화면 정보를 그대로 전달한다. 서버는 해석하지 않는다.
        JsonNode screenContext,

        // 요청이 발생한 경로. 현재는 WEB 고정이며 추후 확장(모바일 등)에 대비한 필드다.
        String requestSource,

        // 응답 언어와 날짜 처리 기준.
        String locale
) {

    // 대화 맥락 한 줄. role은 USER 또는 AGENT 문자열이다.
    // 엔티티의 AgentRole을 그대로 쓰지 않는 이유는, 외부 계약이 우리 enum 변경에 끌려다니지 않게 하기 위해서다.
    public record ContextMessage(
            String role,
            String content
    ) {
    }
}
