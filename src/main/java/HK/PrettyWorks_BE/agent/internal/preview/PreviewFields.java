package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import tools.jackson.databind.JsonNode;

import java.text.NumberFormat;
import java.util.Locale;

// 승인 카드 렌더러 공용 필드 추출.
//
// params는 LLM이 만든 JSON이라 어떤 모양이든 올 수 있다. 여기서 형태가 어긋나면 카드를 그리지 않고
// 실패시킨다 — 사용자가 "빈 칸"이나 "null"이 적힌 카드를 승인하는 상황을 만들지 않기 위해서다.
final class PreviewFields {

    private PreviewFields() {
    }

    static JsonNode object(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return params;
    }

    static String requiredText(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return value.textValue();
    }

    // 없거나 null이면 fallback. 승인 카드에 "null"이라는 글자가 찍히지 않게 한다.
    static String optionalText(JsonNode params, String field, String fallback) {
        JsonNode value = params.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return fallback;
        }
        return value.textValue();
    }

    static long requiredNumber(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return value.longValue();
    }

    static boolean requiredBoolean(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isBoolean()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return value.booleanValue();
    }

    static int arraySize(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isArray()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return value.size();
    }

    // 금액은 틀리면 피해가 가장 크다. "십이만원"을 1200000으로 잘못 읽었을 때
    // 사용자가 알아챌 유일한 기회라 천 단위 구분을 넣는다.
    static String money(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
