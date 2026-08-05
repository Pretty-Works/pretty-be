package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.service.ApprovalPreviewRenderer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 금액을 맨 앞에 둔다. 지출은 v1에 수정 도구가 없어 잘못 넣으면 화면에서 직접 고쳐야 하고,
// 회계 집계까지 어긋난다.
@Component
public class ExpenseCreatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "expense.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        return "지출을 등록합니다."
                + "\n- 금액: " + PreviewFields.money(PreviewFields.requiredNumber(body, "amount"))
                + "\n- 사용일: " + PreviewFields.requiredText(body, "expenseDate")
                + "\n- 유형: " + PreviewFields.requiredText(body, "category")
                + "\n- 사용처: " + PreviewFields.requiredText(body, "merchant")
                + "\n- 목적: " + PreviewFields.requiredText(body, "purpose")
                + "\n- 프로젝트 #" + PreviewFields.requiredNumber(body, "projectId");
    }
}
