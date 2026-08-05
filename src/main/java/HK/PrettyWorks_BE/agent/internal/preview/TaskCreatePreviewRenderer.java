package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.service.ApprovalPreviewRenderer;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.task.policy.TaskPolicy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TaskCreatePreviewRenderer implements ApprovalPreviewRenderer {

    private static final int MAX_PREVIEW_CONTENT = 60;

    @Override
    public String tool() {
        return "task.create";
    }

    @Override
    public String render(JsonNode params) {
        // 요청 바디는 {"tasks":[...]} 형태다. 최상위를 배열로 두지 않은 이유는
        // AgentTaskCreateRequest 주석 참고.
        JsonNode tasks = params == null ? null : params.get("tasks");
        if (tasks == null || !tasks.isArray() || tasks.isEmpty()
                || tasks.size() > TaskPolicy.MAX_CREATE_BATCH_SIZE) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        StringBuilder preview = new StringBuilder("할 일 ")
                .append(tasks.size())
                .append("건을 추가합니다.");
        for (int index = 0; index < tasks.size(); index++) {
            JsonNode task = tasks.get(index);
            JsonNode content = task == null ? null : task.get("content");
            JsonNode dueDate = task == null ? null : task.get("dueDate");
            if (task == null || !task.isObject()
                    || content == null || !content.isTextual() || content.textValue().isBlank()
                    || dueDate == null || !dueDate.isTextual() || dueDate.textValue().isBlank()) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }

            preview.append("\n- ")
                    .append(shorten(content.textValue()))
                    .append(" · ")
                    .append(dueDate.textValue());

            JsonNode projectId = task.get("projectId");
            if (projectId == null || projectId.isNull()) {
                preview.append(" · 개인");
            } else if (projectId.isIntegralNumber()) {
                preview.append(" · 프로젝트 #").append(projectId.longValue());
            } else {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
        }
        return preview.toString();
    }

    private String shorten(String content) {
        if (content.length() <= MAX_PREVIEW_CONTENT) {
            return content;
        }
        return content.substring(0, MAX_PREVIEW_CONTENT - 1) + "…";
    }
}
