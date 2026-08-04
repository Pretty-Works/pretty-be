package HK.PrettyWorks_BE.agent.dto.res;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

// 메시지 전송 결과 DTO.
//
// steps·action은 에이전트 서버가 만든 값을 그대로 통과시킨다(JsonNode). 서버가 구조를 정의하면
// LLM팀이 필드를 추가할 때마다 BE 배포가 필요해진다. 해석은 프론트가 한다.
@Builder
public record AgentMessageResponse(

        // 새 스레드였으면 방금 생성된 id. 프론트는 이 값으로 대화를 이어간다.
        Long conversationId,

        // 저장된 AGENT 메시지 id. 승인·취소 API에 그대로 쓴다.
        Long messageId,

        // 말풍선에 표시할 답변.
        String answer,

        // 에이전트 처리 성공 여부. 에이전트 서버가 직접 판정해 내려준 값이다.
        boolean success,

        // 답변의 근거로 삼은 과정. 말풍선 아래 "참고한 내용 N건" 접이식으로 표시한다.
        // 현재 규격은 문자열 배열이며, 없으면 null.
        JsonNode steps,

        // 후속 동작 제안. 없으면 null.
        // requiresApproval이 true면 이 메시지가 PENDING으로 저장되어 스레드가 잠긴다.
        JsonNode action
) {
}
