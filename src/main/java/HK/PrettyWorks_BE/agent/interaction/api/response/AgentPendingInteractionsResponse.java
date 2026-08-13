package HK.PrettyWorks_BE.agent.interaction.api.response;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record AgentPendingInteractionsResponse(
        @Schema(description = "대기 중인 카드 수. items의 길이와 항상 같다.", example = "2")
        int totalCount,

        List<PendingInteraction> items
) {
    public AgentPendingInteractionsResponse {
        items = List.copyOf(items);
        totalCount = items.size();
    }

    public record PendingInteraction(
            @Schema(description = "APPROVAL(승인 카드) · QUESTION(되묻기)", example = "QUESTION")
            AgentInteractionKind kind,

            @Schema(description = "APPROVAL이면 approvalId, QUESTION이면 questionId로 그대로 쓴다.",
                    example = "51")
            Long interactionId,

            @Schema(description = "카드 제목", example = "휴가 날짜 선택")
            String label,

            // 카드 원문(payload)을 통째로 내리는 대신 화면이 바로 그릴 수 있는 모양으로 펼친다.
            @ArraySchema(arraySchema = @Schema(description = """
                    화면에 그릴 버튼 목록.
                    QUESTION이면 고른 id를 그대로 selectedOptionIds에 담아 보냅니다.
                    APPROVAL이면 APPROVE는 decision=APPROVED, REJECT는 decision=REJECTED,
                    그 밖의 id는 decision=ALTERNATIVE + alternativeId=id로 보냅니다.
                    (ALWAYS만 예외로 decision=APPROVED + alternativeId=ALWAYS입니다.)"""))
            List<Option> options,

            @Schema(description = "선택지를 여러 개 고를 수 있는지. APPROVAL은 항상 false다.",
                    example = "false")
            boolean multiple,

            @Schema(example = "15")
            Long conversationId,

            @Schema(example = "run_c02b58")
            String runId,

            @Schema(example = "8월 휴가 일정 추천")
            String conversationTitle,

            @Schema(description = "APPROVAL 카드에 접어 보여줄 요약. QUESTION은 null이다.",
                    nullable = true, example = "· 회의명: 스프린트 리뷰 4차")
            String previewText,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss",
                    timezone = "Asia/Seoul")
            @Schema(description = "카드가 뜬 시각", example = "2026-08-03 14:20:11")
            LocalDateTime requestedAt,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss",
                    timezone = "Asia/Seoul")
            @Schema(description = "이 시각이 지나면 카드가 닫히고 실행도 만료된다.",
                    example = "2026-08-03 14:50:11")
            LocalDateTime expiresAt
    ) {
        public PendingInteraction {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    @Schema(name = "PendingInteractionOption")
    public record Option(
            @Schema(description = "승인·질문 응답 API에 그대로 되돌려 보낼 id", example = "APPROVE")
            String id,

            @Schema(description = "버튼에 찍을 문구", example = "저장")
            String label,

            @Schema(description = "이름표만으로 차이를 알 수 없는 보기의 부가 설명. 없으면 null",
                    nullable = true,
                    example = "마일스톤 목표일을 2주 뒤로 미루고 작업 3건의 마감일도 함께 조정합니다. 리스크: 낮음")
            String description
    ) {
    }
}
