package HK.PrettyWorks_BE.agent.suggestion.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 에이전트 패널 추천 문구 응답.
 *
 * <p>{@code suggestions[]}의 각 항목은 FastAPI가 만든 원문 그대로입니다 —
 * {@code text} · {@code prompt} · {@code kind}. 필드를 풀어 두지 않는 것이 핵심입니다.
 * BE가 스키마를 한 벌 더 복사해 두면 LLM팀이 {@code kind}를 하나 추가할 때마다 BE 배포가
 * 필요해집니다({@code ProjectSummaryResponse}와 같은 이유).</p>
 */
public record AgentSuggestionResponse(

        // implementation = Object.class 를 빼면 JsonNode 게터가 전부 필드로 펼쳐진다
        // (AgentMessageRequest.screenContext 주석 참고).
        @ArraySchema(
                schema = @Schema(implementation = Object.class),
                arraySchema = @Schema(
                        description = "추천 칩 0~3개. 화면에는 text를 걸고, 눌렀을 때는 text가 아니라 "
                                + "prompt를 메시지 전송 API의 goal로 보내야 합니다 — text는 물음형이라 "
                                + "그대로 보내면 에이전트가 되묻습니다. 빈 배열이면 추천 영역을 숨기면 됩니다.",
                        example = """
                                [
                                  {
                                    "text": "'환율 연동 검증' 마감이 지났습니다. 처리해드릴까요?",
                                    "prompt": "'환율 연동 검증' 마감이 지난 할 일을 처리해줘",
                                    "kind": "overdue_task"
                                  }
                                ]
                                """))
        List<JsonNode> suggestions
) {
    public AgentSuggestionResponse {
        suggestions = copyOf(suggestions);
    }

    @Override
    public List<JsonNode> suggestions() {
        return copyOf(suggestions);
    }

    // JsonNode는 가변이라 목록만 불변으로 감싸면 원소를 그대로 고칠 수 있다.
    private static List<JsonNode> copyOf(List<JsonNode> suggestions) {
        return suggestions.stream().<JsonNode>map(JsonNode::deepCopy).toList();
    }
}
