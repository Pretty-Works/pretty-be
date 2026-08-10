package HK.PrettyWorks_BE.agent.execution.gateway;

import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEventType;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentAccessType;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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

    private final ObjectMapper objectMapper;

    public AgentServerEventDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        return new AgentServerEvent.Step(requiredSingleLineText(payload, "text", 100));
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
                requiredSingleLineText(payload, "summary", 60),
                requiredText(payload, "previewText", null),
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
                    id, requiredSingleLineText(alternative, "label", 30)));
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
                    requiredSingleLineText(option, "label", 60),
                    optionalText(option, "description")
            ));
        }
        boolean allowFreeText = optionalBoolean(payload, "allowFreeText", true);
        if (options.isEmpty() && !allowFreeText) {
            throw invalid("question must provide an answer path");
        }
        return new AgentServerEvent.Question(
                requiredSingleLineText(payload, "label", 60),
                requiredText(payload, "text", 200),
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
        return new AgentServerEvent.Done(requiredText(payload, "answer", null), action);
    }

    private AgentServerEvent.Action decodeAction(JsonNode action) {
        if (!action.isObject()) {
            throw invalid("done action must be an object or null");
        }
        String type = requiredText(action, "type", 20);
        if (!"NAVIGATE".equals(type) && !"FILL_FORM".equals(type)) {
            throw invalid("done action type is invalid");
        }
        JsonNode params = optionalObject(action, "params");
        JsonNode formData = optionalObject(action, "formData");
        if ("FILL_FORM".equals(type) && formData == null) {
            throw invalid("FILL_FORM requires formData");
        }
        if ("NAVIGATE".equals(type) && formData != null) {
            throw invalid("NAVIGATE must not contain formData");
        }
        return new AgentServerEvent.Action(
                type,
                requiredSingleLineText(action, "label", 30),
                requiredSingleLineText(action, "targetScreen", null),
                params,
                formData
        );
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
