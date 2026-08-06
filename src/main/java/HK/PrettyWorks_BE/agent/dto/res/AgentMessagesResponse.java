package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

// 스레드 상세 DTO. 대화 내역에서 하나를 고르면 말풍선을 이 응답으로 복원한다.
//
// rawJson은 담지 않는다. 연동 디버깅용 원본이라 화면에 쓸 일이 없고, 메시지 수만큼 딸려오면 무겁다.
@Builder
public record AgentMessagesResponse(
        Long conversationId,
        String title,
        boolean autoApprove,
        String activeRunId,
        AgentRunStatus activeRunStatus,

        // 오래된 순. 프론트는 받은 순서대로 위에서 아래로 그리면 된다.
        List<MessageItem> messages
) {

    @Builder
    public record MessageItem(
            Long messageId,
            Long runId,

            // USER면 오른쪽 보라, AGENT면 왼쪽 회색.
            AgentRole role,

            String content,

            // AGENT 행만 값이 있다. false면 실패 안내 말풍선이다.
            Boolean success,

            // "참고한 내용 N건" 접이식. 없으면 null.
            // implementation = Object.class 를 빼면 JsonNode 게터가 전부 필드로 펼쳐진다(AgentMessageRequest 주석 참고).
            @Schema(implementation = Object.class,
                    description = "\"참고한 내용 N건\" 접이식에 뿌릴 단계 목록. 없으면 null.",
                    example = "[{\"text\": \"할 일 3건을 찾았어요\"}]")
            JsonNode steps,

            // done.action 종류. 프론트가 NAVIGATE/FILL_FORM 버튼 동작을 정한다.
            @Schema(description = "done.action의 종류. NAVIGATE·FILL_FORM 등. 없으면 null.",
                    nullable = true, example = "NAVIGATE")
            String actionType,

            // done.action 원문. targetScreen·params·formData를 프론트가 해석한다.
            @Schema(implementation = Object.class,
                    description = "done.action 원문. targetScreen·params·formData를 프론트가 해석한다.",
                    example = "{\"type\": \"NAVIGATE\", \"label\": \"할 일 보기\", "
                            + "\"targetScreen\": \"TASK_LIST\", \"params\": {\"projectId\": 3}}")
            JsonNode action,

            LocalDateTime createdAt
    ) {
    }
}
