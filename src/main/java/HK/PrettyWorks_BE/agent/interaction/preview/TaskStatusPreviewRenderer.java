package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 비슷한 할 일이 여럿일 때 엉뚱한 것을 완료 처리하면 알아차리기 어렵다.
// 카드에는 id밖에 실을 수 없어(params에 내용이 없다) 대상 id를 분명히 보여준다.
@Component
public class TaskStatusPreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "task.toggleStatus";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        boolean completed = PreviewFields.requiredBoolean(body, "completed");

        return "할 일 #" + PreviewFields.requiredNumber(body, "taskId")
                + " 을 " + (completed ? "완료" : "미완료") + " 처리합니다.";
    }
}
