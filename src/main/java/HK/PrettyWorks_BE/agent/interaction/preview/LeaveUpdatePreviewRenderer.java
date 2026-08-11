package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 부분 수정. 전달된 필드만 적고 나머지는 유지된다는 것을 명시한다.
// reason은 빈 문자열이 "사유 비우기"라는 별도의 뜻을 가져 따로 처리한다.
@Component
public class LeaveUpdatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "leave.update";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        StringBuilder preview = new StringBuilder("휴가 #")
                .append(PreviewFields.requiredNumber(body, "leaveId"))
                .append(" 를 수정합니다.");

        appendIfPresent(preview, body, "leaveType", "유형");
        appendIfPresent(preview, body, "startDate", "시작일");
        appendIfPresent(preview, body, "endDate", "종료일");
        appendReason(preview, body);

        return preview.append("\n- 나머지 항목은 그대로 유지됩니다.").toString();
    }

    private void appendIfPresent(StringBuilder preview, JsonNode body, String field, String label) {
        JsonNode value = body.get(field);
        if (value != null && value.isTextual() && !value.textValue().isBlank()) {
            preview.append("\n- ").append(label).append(": ").append(value.textValue());
        }
    }

    private void appendReason(StringBuilder preview, JsonNode body) {
        JsonNode reason = body.get("reason");
        if (reason == null || reason.isNull() || !reason.isTextual()) {
            return;
        }
        preview.append(reason.textValue().isEmpty()
                ? "\n- 사유: 삭제(비웁니다)"
                : "\n- 사유: " + reason.textValue());
    }
}
