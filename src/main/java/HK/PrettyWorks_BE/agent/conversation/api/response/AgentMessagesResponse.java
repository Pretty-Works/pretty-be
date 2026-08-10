package HK.PrettyWorks_BE.agent.conversation.api.response;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
        List<MessageItem> messages,

        // 말풍선 사이에 다시 그릴 승인 카드. 오래된 순이며, 이미 답한 카드도 결과와 함께 남는다.
        List<ApprovalItem> approvals
) {

    // 지난 승인 카드 한 장. 라이브로 받았던 approval_request 이벤트를 결과까지 얹어 복원한 것이다.
    @Builder
    public record ApprovalItem(
            @Schema(description = "승인 응답 API의 경로 변수로 그대로 쓴다.", example = "42")
            Long approvalId,

            @Schema(description = "라이브로 받았던 SSE approval_request 이벤트의 id(seq)와 같은 값. "
                    + "실행 안에서만 순서가 보장되고, 이벤트가 이미 정리된 오래된 카드는 null이다.",
                    nullable = true, example = "5")
            Long seq,

            @Schema(description = "READ · WRITE", example = "WRITE")
            AgentAccessType access,

            @Schema(description = "카드 제목", example = "회의록 저장 · 스프린트 리뷰 4차")
            String summary,

            @Schema(description = "카드에 접어 보여줄 요약", nullable = true,
                    example = "· 회의명: 스프린트 리뷰 4차")
            String previewText,

            @ArraySchema(arraySchema = @Schema(description = "카드에 떠 있던 대안 버튼. "
                    + "'항상 허용'(ALWAYS)은 서버가 붙이는 예약 선택지라 목록에 함께 들어 있다."))
            List<Alternative> alternatives,

            @Schema(description = "PENDING(대기) · APPROVED(승인) · REJECTED(거절) · ALTERNATIVE(대안 선택) · EXPIRED(만료)",
                    example = "ALTERNATIVE")
            AgentInteractionStatus status,

            @Schema(description = "ALTERNATIVE일 때 고른 대안 id. 그 밖에는 null이다.",
                    nullable = true, example = "FILL_FORM")
            String chosenAlternativeId,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss",
                    timezone = "Asia/Seoul")
            @Schema(description = "답한 시각. 아직 대기 중이면 null이다.",
                    nullable = true, example = "2026-08-02 14:25:12")
            LocalDateTime decidedAt
    ) {
        public ApprovalItem {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        }
    }

    public record Alternative(
            @Schema(example = "FILL_FORM") String id,
            @Schema(example = "작성 화면에서 직접 고칠래요") String label
    ) {
    }

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

            // done.action 종류. 프론트가 버튼 동작을 정한다.
            // OPEN_EXTERNAL_URL은 사내 화면이 아니라 새 창으로 나가므로 targetScreen이 null이고
            // params.url을 연다(외부 인증 진입점).
            @Schema(description = "done.action의 종류. NAVIGATE·FILL_FORM·OPEN_EXTERNAL_URL 등. 없으면 null.",
                    nullable = true, example = "NAVIGATE")
            String actionType,

            // done.action 원문. targetScreen·params·formData를 프론트가 해석한다.
            @Schema(implementation = Object.class,
                    description = "done.action 원문. targetScreen·params·formData를 프론트가 해석한다.",
                    example = "{\"type\": \"NAVIGATE\", \"label\": \"할 일 보기\", "
                            + "\"targetScreen\": \"TASK_LIST\", \"params\": {\"projectId\": 3}}")
            JsonNode action,

            // 이 말풍선에 붙었던 파일. USER 행에만 값이 있고, 없으면 빈 배열이다.
            @ArraySchema(arraySchema = @Schema(
                    description = "붙였던 파일(사용자가 고른 순서). 내용은 보관하지 않아 내려받을 수 없다."))
            List<AttachmentItem> attachments,

            LocalDateTime createdAt
    ) {
        public MessageItem {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    // 첨부 파일 한 개. 다시 내려받을 URL이 없는 것이 의도다 —
    // BE는 파일을 에이전트로 넘기는 게이트일 뿐 저장소가 아니다(AgentMessageAttachmentEntity 주석 참고).
    public record AttachmentItem(
            @Schema(description = "업로드 당시 파일명", example = "회의록.txt")
            String filename,

            @Schema(description = "MIME 타입", example = "text/plain")
            String contentType,

            @Schema(description = "원본 바이트 크기", example = "20480")
            long sizeBytes
    ) {
    }
}
