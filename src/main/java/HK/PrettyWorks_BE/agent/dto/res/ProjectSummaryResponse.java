package HK.PrettyWorks_BE.agent.dto.res;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로젝트 탭 상단 AI 요약 배너 응답.
 *
 * <p>{@code summaries[]}의 각 항목은 FastAPI가 만든 원문 그대로입니다 —
 * {@code section} · {@code headline} · {@code detail[]} · {@code stats[]}.
 * 필드를 풀어 두지 않는 것이 핵심입니다. BE가 스키마를 한 벌 더 복사해 두면
 * LLM팀이 칩을 하나 추가할 때마다 BE 배포가 필요해집니다.</p>
 */
public record ProjectSummaryResponse(
        Long projectId,

        @Schema(description = "요약을 마지막으로 생성한 시각. 아직 만들어진 요약이 없으면 null입니다.",
                example = "2026-08-07T04:00:12")
        LocalDateTime generatedAt,

        // implementation = Object.class 를 빼면 JsonNode 게터가 전부 필드로 펼쳐진다
        // (AgentMessageRequest.screenContext 주석 참고).
        @ArraySchema(
                schema = @Schema(implementation = Object.class),
                arraySchema = @Schema(
                        description = "배너 목록. overview → board → budget → meeting 순서로 내려갑니다. "
                                + "아직 생성 전이면 빈 배열입니다 — 에러가 아니라 '배너를 그리지 않으면 되는' 상태입니다.",
                        example = """
                                [
                                  {
                                    "section": "budget",
                                    "headline": "집행률 62%, 외주비 비중이 65%로 가장 커요",
                                    "detail": ["집행률이 기간 경과율보다 8%p 빠르네요.", "잔여 예산은 ₩11,400,000 남았어요."],
                                    "stats": [{"label": "집행률", "value": "62%"}, {"label": "잔여", "value": "₩11.4M"}]
                                  }
                                ]
                                """))
        List<JsonNode> summaries
) {
    public ProjectSummaryResponse {
        summaries = copyOf(summaries);
    }

    @Override
    public List<JsonNode> summaries() {
        return copyOf(summaries);
    }

    // JsonNode는 가변이라 목록만 불변으로 감싸면 원소를 그대로 고칠 수 있다.
    private static List<JsonNode> copyOf(List<JsonNode> summaries) {
        return summaries.stream().<JsonNode>map(JsonNode::deepCopy).toList();
    }
}
