package HK.PrettyWorks_BE.agent.client.dto;

import HK.PrettyWorks_BE.agent.constant.AgentAccessType;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * FastAPI가 한 SSE 세그먼트에서 방출할 수 있는 다섯 이벤트의 검증된 표현입니다.
 * 외부 JSON을 직접 record로 역직렬화하지 않고 AgentServerEventDecoder를 통과시켜 만듭니다.
 */
public sealed interface AgentServerEvent permits
        AgentServerEvent.Step,
        AgentServerEvent.ApprovalRequest,
        AgentServerEvent.Question,
        AgentServerEvent.Done,
        AgentServerEvent.Failure {

    AgentServerEventType type();

    record Step(String text) implements AgentServerEvent {
        @Override
        public AgentServerEventType type() {
            return AgentServerEventType.STEP;
        }
    }

    record ApprovalRequest(
            String toolCallId,
            String tool,
            AgentAccessType access,
            String summary,
            String previewText,
            JsonNode params,
            List<Alternative> alternatives
    ) implements AgentServerEvent {
        public ApprovalRequest {
            params = params.deepCopy();
            alternatives = List.copyOf(alternatives);
        }

        @Override
        public JsonNode params() {
            return params.deepCopy();
        }

        @Override
        public AgentServerEventType type() {
            return AgentServerEventType.APPROVAL_REQUEST;
        }
    }

    record Alternative(String id, String label) {
    }

    record Question(
            String label,
            String text,
            List<QuestionOption> options,
            boolean multiple,
            boolean allowFreeText
    ) implements AgentServerEvent {
        public Question {
            options = List.copyOf(options);
        }

        @Override
        public AgentServerEventType type() {
            return AgentServerEventType.QUESTION;
        }
    }

    record QuestionOption(String id, String label, String description) {
    }

    record Done(String answer, Action action) implements AgentServerEvent {
        @Override
        public AgentServerEventType type() {
            return AgentServerEventType.DONE;
        }
    }

    record Action(
            String type,
            String label,
            String targetScreen,
            JsonNode params,
            JsonNode formData
    ) {
        public Action {
            params = copyNullable(params);
            formData = copyNullable(formData);
        }

        @Override
        public JsonNode params() {
            return copyNullable(params);
        }

        @Override
        public JsonNode formData() {
            return copyNullable(formData);
        }
    }

    record Failure(String code, String message) implements AgentServerEvent {
        @Override
        public AgentServerEventType type() {
            return AgentServerEventType.ERROR;
        }
    }

    private static JsonNode copyNullable(JsonNode node) {
        return node == null ? null : node.deepCopy();
    }
}
