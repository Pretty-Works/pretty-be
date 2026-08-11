package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 부분 수정이라 "보내지 않은 필드는 그대로"라는 사실 자체가 카드에 드러나야 한다.
// 전달된 필드만 나열하고, 참가자는 세 가지 뜻을 구분해 적는다.
@Component
public class ScheduleUpdatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "schedule.update";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        StringBuilder preview = new StringBuilder("일정 #")
                .append(PreviewFields.requiredNumber(body, "scheduleId"))
                .append(" 을 수정합니다.");

        appendIfPresent(preview, body, "title", "제목");
        appendIfPresent(preview, body, "startAt", "시작");
        appendIfPresent(preview, body, "endAt", "종료");
        appendIfPresent(preview, body, "type", "유형");
        appendParticipants(preview, body);

        return preview.append("\n- 나머지 항목은 그대로 유지됩니다.").toString();
    }

    private void appendIfPresent(StringBuilder preview, JsonNode body, String field, String label) {
        JsonNode value = body.get(field);
        if (value != null && value.isTextual() && !value.textValue().isBlank()) {
            preview.append("\n- ").append(label).append(": ").append(value.textValue());
        }
    }

    // null=유지라 아무것도 적지 않고, 빈 배열은 "나 혼자만 남는다"는 뜻이라 반드시 알려야 한다.
    // 사용자가 "박지원님 추가"라고 말했는데 목록이 통째로 교체되는 경우도 여기서 드러난다.
    private void appendParticipants(StringBuilder preview, JsonNode body) {
        JsonNode participants = body.get("participantUserIds");
        if (participants == null || participants.isNull() || !participants.isArray()) {
            return;
        }
        if (participants.isEmpty()) {
            preview.append("\n- 참가자: 전원 제외(작성자 혼자 남습니다)");
            return;
        }
        preview.append("\n- 참가자: ").append(participants.size())
                .append("명으로 교체(기존 명단은 대체됩니다)");
    }
}
