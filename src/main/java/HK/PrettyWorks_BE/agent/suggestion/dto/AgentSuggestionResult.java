package HK.PrettyWorks_BE.agent.suggestion.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * FastAPI {@code POST /api/agent/suggestions} 응답.
 *
 * <p>BE는 게이트라서 추천 문구를 해석하지 않습니다. {@code text}·{@code prompt}·{@code kind}를
 * 필드로 모델링하지 않고 칩 하나를 통째로 {@link JsonNode}로 들고 다니는 이유입니다 —
 * LLM팀이 {@code kind}를 하나 더 넣거나 필드를 추가해도 BE 수정·배포가 필요 없어야 합니다.</p>
 *
 * <p>BE가 값을 읽는 곳은 {@link HK.PrettyWorks_BE.agent.suggestion.gateway.AgentSuggestionClient}의
 * 유효성 검사 한 곳뿐이고, 거기서도 "화면에 걸 수 있는 칩인가"만 봅니다(문구 자체는 건드리지 않음).</p>
 */
public record AgentSuggestionResult(List<JsonNode> suggestions) {

    public AgentSuggestionResult {
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
