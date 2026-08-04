package HK.PrettyWorks_BE.agent.client.dto;

import tools.jackson.databind.JsonNode;

// 에이전트 서버(FastAPI)가 돌려주는 응답 원문.
//
// 모든 필드가 null일 수 있다고 보고 다룬다. 상대 서버가 스키마를 바꾸거나 일부 필드를 빼도
// 역직렬화 자체는 성공하기 때문에, 필수값 검사는 서비스에서 하고 없으면 AGENT_007로 처리한다.
public record AgentRawResponse(

        // 말풍선에 표시할 답변. 이 값이 비어 있으면 응답이 깨진 것으로 본다.
        String answer,

        // 에이전트 처리 성공 여부. 업무 API를 부르지 않는 턴("안녕" 같은)도 있어서
        // 호출 이력으로 판정하지 않고 에이전트 서버가 직접 내려준 값을 쓴다.
        Boolean success,

        // 첫 응답에서만 내려온다. 스레드 제목으로 쓰고, 없으면 질문 앞부분으로 폴백한다.
        String conversationTitle,

        // 답변 근거로 삼은 과정. 구조를 해석하지 않고 그대로 저장·전달한다.
        JsonNode steps,

        // 후속 동작 제안. type·requiresApproval·targetScreen·params·formData·options 등이 들어 있다.
        // 서버가 읽는 것은 type과 requiresApproval 두 개뿐이고 나머지는 통과시킨다.
        JsonNode action
) {
}
