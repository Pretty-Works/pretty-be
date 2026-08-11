package HK.PrettyWorks_BE.agent.execution.gateway;

import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEventType;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * SSE의 event/data 문자열을 의미 검증된 v2 이벤트로 바꿉니다.
 *
 * <p>알 수 없는 JSON 필드는 허용합니다. 필드 추가만으로 양 팀 배포 순서가 강제되지 않게 하기 위해서입니다.
 * 반면 서버가 주입하는 식별자와 권한 경계는 엄격히 거부합니다.</p>
 */
@Component
public class AgentServerEventDecoder {
    private static final Pattern AGENT_ERROR_CODE = Pattern.compile("AGENT_\\d{3}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // done.action 종류. 앞의 둘은 사내 화면으로 가고, OPEN_EXTERNAL_URL은 새 창으로 나간다
    // (외부 인증 진입점 — Gmail OAuth 등). 종류마다 필수 필드가 달라 아래 decodeAction에서 갈린다.
    private static final String NAVIGATE = "NAVIGATE";
    private static final String FILL_FORM = "FILL_FORM";
    private static final String OPEN_EXTERNAL_URL = "OPEN_EXTERNAL_URL";
    private static final Set<String> ACTION_TYPES = Set.of(NAVIGATE, FILL_FORM, OPEN_EXTERNAL_URL);

    private static final Set<String> EXTERNAL_URL_SCHEMES = Set.of("http", "https");

    private final ObjectMapper objectMapper;

    // 새 창으로 열어도 되는 origin 목록(scheme://host[:port]). 에이전트가 만든 링크를 사내 도구
    // 안에서 클릭하게 만드는 기능이라, 목적지를 서버에서 묶지 않으면 프롬프트 인젝션 한 번으로
    // "Gmail 연동하기"처럼 보이는 피싱 버튼이 그려진다.
    private final Set<String> allowedExternalOrigins;

    public AgentServerEventDecoder(
            ObjectMapper objectMapper,
            @Value("${agent.external-url.allowed-origins}") String allowedExternalOrigins) {
        this.objectMapper = objectMapper;
        this.allowedExternalOrigins = parseOrigins(allowedExternalOrigins);
        // 비어 있으면 뜨지 못하게 한다. 기본값을 "전부 허용"으로 두면 설정을 깜빡한 환경이
        // 조용히 무방비가 되고, 경고 로그는 아무도 읽지 않는다(agent.internal.api-key와 같은 방식).
        if (this.allowedExternalOrigins.isEmpty()) {
            throw new IllegalStateException("agent.external-url.allowed-origins must not be blank");
        }
    }

    private static Set<String> parseOrigins(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public DecodedAgentServerEvent decode(String eventName, String data) {
        AgentServerEventType type = eventType(eventName);
        ObjectNode payload = payload(type, data);
        AgentServerEvent event = switch (type) {
            case STEP -> decodeStep(payload);
            case APPROVAL_REQUEST -> decodeApproval(payload);
            case QUESTION -> decodeQuestion(payload);
            case DONE -> decodeDone(payload);
            case ERROR -> decodeError(payload);
        };
        return new DecodedAgentServerEvent(event, payload);
    }

    private AgentServerEventType eventType(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw invalid("event name is missing");
        }
        try {
            return AgentServerEventType.fromWireValue(eventName);
        } catch (IllegalArgumentException exception) {
            throw invalid("unknown event name", exception);
        }
    }

    private ObjectNode payload(AgentServerEventType type, String data) {
        if (data == null || data.isBlank()) {
            throw invalid("event data is missing");
        }
        int payloadBytes = data.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > AgentPayloadLimits.MAX_EVENT_DATA_BYTES
                || (type == AgentServerEventType.STEP
                && payloadBytes > AgentPayloadLimits.MAX_STEP_DATA_BYTES)) {
            throw invalid("event data is too large");
        }
        if (data.indexOf('\n') >= 0 || data.indexOf('\r') >= 0) {
            throw invalid("event data must be one line");
        }
        try {
            JsonNode payload = objectMapper.readTree(data);
            if (payload == null || !payload.isObject()) {
                throw invalid("event data must be a JSON object");
            }
            return (ObjectNode) payload;
        } catch (AgentServerEventDecodingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("event data is not valid JSON", exception);
        }
    }

    private AgentServerEvent decodeStep(JsonNode payload) {
        return new AgentServerEvent.Step(displayText(payload, "text", 100));
    }

    private AgentServerEvent decodeApproval(JsonNode payload) {
        rejectServerOwnedField(payload, "approvalId");
        AgentAccessType access = access(payload);
        if (access != AgentAccessType.WRITE) {
            throw invalid("READ tools must not emit approval_request");
        }

        JsonNode params = requiredObject(payload, "params");
        List<AgentServerEvent.Alternative> alternatives = alternatives(payload.get("alternatives"));
        return new AgentServerEvent.ApprovalRequest(
                requiredText(payload, "toolCallId", 64),
                requiredText(payload, "tool", 50),
                access,
                displayText(payload, "summary", 60),
                // 미리보기는 params를 줄줄이 적은 값이라 params가 비면 함께 빈다.
                // 그 자체로 승인 카드를 못 만들 이유는 없으므로 빈 문자열을 허용한다.
                optionalTextOrEmpty(payload, "previewText"),
                params,
                alternatives
        );
    }

    private AgentAccessType access(JsonNode payload) {
        String value = requiredText(payload, "access", 10);
        try {
            return AgentAccessType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("approval access is invalid", exception);
        }
    }

    private List<AgentServerEvent.Alternative> alternatives(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid("alternatives must be an array");
        }
        List<AgentServerEvent.Alternative> alternatives = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode alternative : node) {
            if (!alternative.isObject()) {
                throw invalid("alternative must be an object");
            }
            String id = requiredText(alternative, "id", 40);
            if ("ALWAYS".equals(id)) {
                throw invalid("ALWAYS is reserved by the BE");
            }
            if (!ids.add(id)) {
                throw invalid("alternative ids must be unique");
            }
            alternatives.add(new AgentServerEvent.Alternative(
                    id, displayText(alternative, "label", 30)));
        }
        return alternatives;
    }

    private AgentServerEvent decodeQuestion(JsonNode payload) {
        rejectServerOwnedField(payload, "questionId");
        JsonNode optionsNode = payload.get("options");
        if (optionsNode == null || !optionsNode.isArray()) {
            throw invalid("question options must be an array");
        }
        List<AgentServerEvent.QuestionOption> options = new ArrayList<>();
        Set<String> optionIds = new HashSet<>();
        for (JsonNode option : optionsNode) {
            if (!option.isObject()) {
                throw invalid("question option must be an object");
            }
            String optionId = requiredText(option, "id", null);
            if (!optionIds.add(optionId)) {
                throw invalid("question option ids must be unique");
            }
            options.add(new AgentServerEvent.QuestionOption(
                    optionId,
                    displayText(option, "label", 60),
                    optionalText(option, "description")
            ));
        }
        boolean allowFreeText = optionalBoolean(payload, "allowFreeText", true);
        if (options.isEmpty() && !allowFreeText) {
            throw invalid("question must provide an answer path");
        }
        return new AgentServerEvent.Question(
                displayText(payload, "label", 60),
                // 질문 본문은 여러 줄일 수 있어 줄바꿈은 남기고 길이만 맞춘다.
                truncate(requiredText(payload, "text", null), 200),
                options,
                optionalBoolean(payload, "multiple", false),
                allowFreeText
        );
    }

    private AgentServerEvent decodeDone(JsonNode payload) {
        JsonNode actionNode = payload.get("action");
        AgentServerEvent.Action action = actionNode == null || actionNode.isNull()
                ? null
                : decodeAction(actionNode);
        // answer가 빈 문자열인 done도 정상 종료로 받는다. 여기서 거절하면 종료 이벤트가
        // 사라져 실행이 AGENT_017(도중에 끊김)로 끝난다 — 빈 답보다 나쁜 결과다.
        return new AgentServerEvent.Done(requiredString(payload, "answer"), action);
    }

    private AgentServerEvent.Action decodeAction(JsonNode action) {
        if (!action.isObject()) {
            throw invalid("done action must be an object or null");
        }
        String type = requiredText(action, "type", 20);
        if (!ACTION_TYPES.contains(type)) {
            throw invalid("done action type is invalid");
        }
        JsonNode params = optionalObject(action, "params");
        JsonNode formData = optionalObject(action, "formData");
        if (FILL_FORM.equals(type)) {
            if (formData == null) {
                throw invalid("FILL_FORM requires formData");
            }
        } else if (formData != null) {
            // 폼을 채울 화면이 없는 종류다. 조용히 버리면 프론트가 안 쓰는 값을 계속 실어 보낸다.
            throw invalid(type + " must not contain formData");
        }
        if (OPEN_EXTERNAL_URL.equals(type)) {
            verifyExternalUrl(params);
        }
        return new AgentServerEvent.Action(
                type,
                displayText(action, "label", 30),
                targetScreen(action, type),
                params,
                formData
        );
    }

    // 사내 화면으로 가는 종류만 targetScreen이 필수다. 외부 링크는 이동할 사내 화면이 없어
    // null로 오는데, 여기서 필수로 두면 OPEN_EXTERNAL_URL이 통째로 거부된다.
    private String targetScreen(JsonNode action, String type) {
        if (OPEN_EXTERNAL_URL.equals(type)) {
            return null;
        }
        return requiredSingleLineText(action, "targetScreen", null);
    }

    // 외부 링크는 params.url이 본체다. 이 값은 그대로 저장돼 새로고침 때 다시 내려가므로
    // 프론트 검증만 믿지 않는다 — 한 번 저장되면 그 대화를 열 때마다 되살아난다.
    //
    // 프론트는 이 창을 noopener 없이 열어야 한다(완료를 window.opener.postMessage로 돌려받는 설계라
    // opener가 필요하다). 그러면 열린 페이지가 원래 탭의 주소를 바꿀 수 있으므로, "믿을 수 있는
    // origin만 열린다"가 그 설계의 전제가 된다. 그 전제를 지키는 게 아래 검사다.
    private void verifyExternalUrl(JsonNode params) {
        if (params == null) {
            throw invalid("OPEN_EXTERNAL_URL requires params.url");
        }
        String origin = originOf(requiredText(params, "url", null));
        if (!allowedExternalOrigins.contains(origin)) {
            throw invalid("params.url origin is not allowed");
        }
    }

    // scheme://host[:port] 만 뽑아 정확히 대조한다. endsWith·contains로 비교하면
    // agent.example.com.evil.com 이 통과한다.
    private String originOf(String url) {
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException malformed) {
            throw invalid("params.url must be an http(s) URL");
        }
        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null || !EXTERNAL_URL_SCHEMES.contains(scheme.toLowerCase())) {
            throw invalid("params.url must be an http(s) URL");
        }
        String origin = scheme.toLowerCase() + "://" + host.toLowerCase();
        return parsed.getPort() < 0 ? origin : origin + ":" + parsed.getPort();
    }

    private AgentServerEvent decodeError(JsonNode payload) {
        String code = requiredText(payload, "code", 20);
        if (!AGENT_ERROR_CODE.matcher(code).matches()) {
            throw invalid("error code is invalid");
        }
        return new AgentServerEvent.Failure(code, requiredText(payload, "message", null));
    }

    private void rejectServerOwnedField(JsonNode payload, String field) {
        if (payload.has(field)) {
            throw invalid(field + " is owned by the BE");
        }
    }

    private String requiredText(JsonNode object, String field, Integer maxLength) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be a non-blank string");
        }
        if (maxLength != null && value.textValue().length() > maxLength) {
            throw invalid(field + " is too long");
        }
        return value.textValue();
    }

    /**
     * 값이 있어야 하지만 비어 있어도 되는 문자열입니다(예: {@code done.answer}).
     */
    private String requiredString(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(field + " must be a string");
        }
        return value.textValue();
    }

    private String optionalTextOrEmpty(JsonNode object, String field) {
        String value = optionalText(object, field);
        return value == null ? "" : value;
    }

    /**
     * 화면에 그대로 그려지는 문자열입니다. 줄바꿈·길이 위반을 거절하지 않고 다듬습니다.
     *
     * <p>이 값들은 의미가 아니라 표시용이라, 한 줄 규칙을 어겼다고 이벤트를 버리면
     * 승인 카드나 진행 문구가 통째로 사라지고 실행은 AGENT_017로 끝납니다. 규칙을
     * 지키게 만드는 비용을 사용자가 치르게 하지 않고 여기서 접습니다. 반면 식별자와
     * 권한 경계(toolCallId·tool·access·option.id·error.code 등)는 그대로 엄격합니다.</p>
     */
    private String displayText(JsonNode object, String field, int maxLength) {
        String value = requiredText(object, field, null);
        return truncate(WHITESPACE.matcher(value).replaceAll(" ").trim(), maxLength);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String optionalText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(field + " must be a string");
        }
        return value.textValue();
    }

    private String requiredSingleLineText(JsonNode object, String field, Integer maxLength) {
        String value = requiredText(object, field, maxLength);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw invalid(field + " must be one line");
        }
        return value;
    }

    private boolean optionalBoolean(JsonNode object, String field, boolean defaultValue) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw invalid(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private JsonNode requiredObject(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isObject()) {
            throw invalid(field + " must be an object");
        }
        return value;
    }

    private JsonNode optionalObject(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw invalid(field + " must be an object");
        }
        return value;
    }

    private AgentServerEventDecodingException invalid(String reason) {
        return new AgentServerEventDecodingException(reason);
    }

    private AgentServerEventDecodingException invalid(String reason, Throwable cause) {
        return new AgentServerEventDecodingException(reason, cause);
    }
}
