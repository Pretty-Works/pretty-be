package HK.PrettyWorks_BE.agent.summary.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * FastAPI {@code POST /api/agent/project-summary} 응답.
 *
 * <p>BE는 게이트라서 요약 본문을 해석하지 않습니다. {@code headline}·{@code detail}·{@code stats}를
 * 필드로 모델링하지 않고 배너 한 장을 통째로 {@link JsonNode}로 들고 다니는 이유입니다 —
 * LLM팀이 칩을 하나 더 넣거나 필드를 추가해도 BE 수정·배포가 필요 없어야 합니다.</p>
 *
 * <p>BE가 유일하게 읽는 값은 {@code section}입니다. 저장 키이자 조회 필터라
 * 이 한 필드만은 형식을 검사합니다(비어 있지 않은 문자열).</p>
 */
public record AgentProjectSummaryResult(Long projectId, List<Summary> summaries) {

    public AgentProjectSummaryResult {
        summaries = List.copyOf(summaries);
    }

    /**
     * @param section overview · board · budget · meeting. BE는 값의 의미를 알지 않고 키로만 씁니다.
     * @param payload 배너 한 장의 원문({@code section}·{@code headline}·{@code detail}·{@code stats}).
     *                응답에서 잘라 내지 않고 통째로 저장했다가 프론트에 그대로 돌려줍니다.
     */
    public record Summary(String section, JsonNode payload) {
        public Summary {
            if (section == null || section.isBlank()) {
                throw new IllegalArgumentException("summaries[].section must not be blank");
            }
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("summaries[] must be a JSON object");
            }
            section = section.trim();
            payload = payload.deepCopy();
        }

        @Override
        public JsonNode payload() {
            return payload.deepCopy();
        }
    }
}
