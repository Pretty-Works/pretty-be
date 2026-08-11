package HK.PrettyWorks_BE.agent.interaction.preview;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.task.policy.TaskPolicy;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TaskCreatePreviewRenderer implements ApprovalPreviewRenderer {

    private static final int MAX_PREVIEW_CONTENT = 60;

    private final UserService userService;

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

        // 담당자 이름을 미리 한 번에 찾는다(N+1 방지). 배정은 승인 카드가 사용자에게
        // 보여줄 마지막 기회라 id만 적으면 누구인지 알 수 없는 채로 승인하게 된다.
        Map<Long, String> assigneeNames = resolveAssigneeNames(tasks);

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

            // 담당자를 지정하지 않으면 요청자 본인이 담당한다. 그때는 굳이 적지 않는다 —
            // 모든 줄에 자기 이름이 붙으면 정작 남에게 배정한 줄이 묻힌다.
            Long assigneeId = assigneeId(task);
            if (assigneeId != null) {
                preview.append(" · 담당 ")
                        .append(assigneeNames.getOrDefault(assigneeId, "#" + assigneeId));
            }
        }
        return preview.toString();
    }

    private Map<Long, String> resolveAssigneeNames(JsonNode tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode task : tasks) {
            Long assigneeId = assigneeId(task);
            if (assigneeId != null) {
                ids.add(assigneeId);
            }
        }
        return ids.isEmpty() ? Map.of() : userService.getNameMap(ids);
    }

    // 미지정(없음·null)이면 본인 담당이라 null을 돌려준다. 숫자가 아닌 값은 저장 단계에서
    // 어차피 거절되므로 승인 카드를 그리기 전에 끊는다.
    private Long assigneeId(JsonNode task) {
        JsonNode assigneeId = task == null ? null : task.get("assigneeId");
        if (assigneeId == null || assigneeId.isNull()) {
            return null;
        }
        if (!assigneeId.isIntegralNumber()) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
        return assigneeId.longValue();
    }

    private String shorten(String content) {
        if (content.length() <= MAX_PREVIEW_CONTENT) {
            return content;
        }
        return content.substring(0, MAX_PREVIEW_CONTENT - 1) + "…";
    }
}
