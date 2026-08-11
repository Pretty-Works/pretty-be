package HK.PrettyWorks_BE.agent.shared.json;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

// 에이전트 응답의 JSON 조각(action·steps)을 다루는 공통 도우미.
//
// 우리는 이 값들의 구조를 정의하지 않고 통째로 저장했다가 그대로 돌려준다. 그래야 LLM팀이
// 필드를 추가해도 BE 수정·배포가 필요 없다. 다만 저장은 문자열, 응답은 JsonNode라 변환이 필요하고
// 그 변환이 실패해도 API 전체가 죽으면 안 되므로 한 곳에 모아 방어한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentJsonSupport {

    private final ObjectMapper objectMapper;

    // 조회용. 깨진 JSON 하나 때문에 목록 조회 전체가 500이 되면 안 되므로 null로 낮춘다.
    public JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException exception) {
            log.warn("[에이전트] 저장된 JSON을 읽지 못해 건너뜁니다. json={}", json, exception);
            return null;
        }
    }

    // v2 step은 행 단위로 보관한다. 응답 계약의 배열은 조회 시에만 조립하며 빈 목록은 null이다.
    public JsonNode readArray(List<String> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return null;
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (String payload : payloads) {
            JsonNode step = read(payload);
            if (step != null) {
                result.add(step);
            }
        }
        return result.isEmpty() ? null : result;
    }

}
