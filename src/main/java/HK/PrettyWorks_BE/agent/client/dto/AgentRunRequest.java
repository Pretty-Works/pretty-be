package HK.PrettyWorks_BE.agent.client.dto;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * FastAPI에 새 실행 세그먼트를 시작하도록 요청하는 v2 계약입니다.
 *
 * <p>{@code userId}는 보내지 않습니다. FastAPI가 업무 데이터를 읽고 쓸 때의 신원은
 * 요청 바디가 아니라 내부 도구의 {@code X-Run-Id}를 Spring이 역산해 결정합니다.
 * {@code conversationId}도 FastAPI 체크포인트 키가 아니므로 노출하지 않습니다.</p>
 */
@Builder
public record AgentRunRequest(
        String runId,
        String goal,
        List<ContextMessage> messages,
        JsonNode screenContext,
        String requestSource,
        String locale
) {
    public AgentRunRequest {
        requireText(runId, "runId");
        requireText(goal, "goal");
        requireText(requestSource, "requestSource");
        requireText(locale, "locale");
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        messages = List.copyOf(messages);
        if (messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException("messages must not contain null");
        }
        if (screenContext == null || !screenContext.isObject()
                || !screenContext.has("screen")
                || !screenContext.get("screen").isTextual()
                || screenContext.get("screen").textValue().isBlank()) {
            throw new IllegalArgumentException("screenContext.screen must be a non-blank string");
        }
        screenContext = screenContext.deepCopy();
    }

    @Override
    public JsonNode screenContext() {
        return screenContext.deepCopy();
    }

    public record ContextMessage(String role, String content) {
        public ContextMessage {
            requireText(role, "messages.role");
            requireText(content, "messages.content");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
