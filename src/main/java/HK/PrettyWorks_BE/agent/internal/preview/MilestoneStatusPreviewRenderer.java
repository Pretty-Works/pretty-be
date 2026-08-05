package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.service.ApprovalPreviewRenderer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 마일스톤 완료 체크는 곧 프로젝트 완료율이라 팀 전체가 보는 숫자를 움직인다.
@Component
public class MilestoneStatusPreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "milestone.toggleStatus";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        boolean completed = PreviewFields.requiredBoolean(body, "completed");

        return "마일스톤 #" + PreviewFields.requiredNumber(body, "milestoneId")
                + " 을 " + (completed ? "완료" : "미완료") + " 처리합니다."
                + "\n- 프로젝트 #" + PreviewFields.requiredNumber(body, "projectId")
                + "\n- 프로젝트 마일스톤 완료율이 함께 바뀝니다.";
    }
}
