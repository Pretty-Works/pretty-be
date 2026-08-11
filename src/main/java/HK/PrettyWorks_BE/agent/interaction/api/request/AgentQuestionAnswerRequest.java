package HK.PrettyWorks_BE.agent.interaction.api.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AgentQuestionAnswerRequest(
        @ArraySchema(
                arraySchema = @Schema(description = "고른 선택지 id 목록. question 이벤트의 options[].id를 그대로 보낸다. "
                        + "자유 입력만 보낼 때는 빈 배열이어도 된다."),
                schema = @Schema(example = "3"))
        @Size(max = 50)
        List<@NotBlank @Size(max = 40) String> selectedOptionIds,

        @Schema(description = "자유 입력 답변(선택, 최대 2000자).",
                nullable = true, example = "그룹웨어 프로젝트로 해 줘")
        @Size(max = 2000) String freeText
) {
    public AgentQuestionAnswerRequest {
        // null 원소는 아래 @NotBlank가 400으로 처리해야 한다. List.copyOf를 쓰면 역직렬화
        // 단계에서 NPE가 먼저 나서 잘못된 클라이언트 입력이 500으로 둔갑한다.
        selectedOptionIds = selectedOptionIds == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(selectedOptionIds));
    }
}
