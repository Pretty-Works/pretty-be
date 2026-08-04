package HK.PrettyWorks_BE.agent.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

// 에이전트 채팅 메시지 전송 요청 DTO.
//
// userId와 이전 대화(messages)는 여기에 없다. 서버가 채운다.
//  - userId  : 바디로 받으면 남의 계정으로 에이전트를 돌릴 수 있다. JWT에서 꺼낸다.
//  - messages: 이미 서버 DB에 있고, 클라이언트가 보내면 남의 대화를 끼워 넣어 조작할 수 있다.
@Builder
public record AgentMessageRequest(

        // 이어갈 스레드. null이면 새 스레드를 만든다(패널의 + 버튼).
        Long conversationId,

        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(min = 2, max = 2000, message = "메시지는 2자 이상 2000자 이하로 입력해 주세요.")
        String goal,

        // 사용자가 보고 있는 화면 정보. 서버는 열어보지 않고 그대로 에이전트 서버에 전달한다.
        // 화면마다 구조가 달라 타입을 정의하지 않는다 — 규격은 FE와 LLM팀이 합의한다.
        // 타입을 정의하면 화면이 늘 때마다 BE가 DTO를 만들고 배포해야 한다.
        JsonNode screenContext
) {
}
