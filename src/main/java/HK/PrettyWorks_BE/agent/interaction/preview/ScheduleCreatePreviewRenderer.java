package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 상대 날짜("다음 주 화요일")를 절대 일시로 바꾼 결과가 맞는지 확인받는 자리다.
@Component
public class ScheduleCreatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "schedule.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        return "일정을 등록합니다."
                + "\n- 제목: " + PreviewFields.requiredText(body, "title")
                + "\n- 시작: " + PreviewFields.requiredText(body, "startAt")
                + "\n- 종료: " + PreviewFields.requiredText(body, "endAt")
                + "\n- 유형: " + PreviewFields.requiredText(body, "type")
                + "\n- 참가자: " + participantCount(body) + "명(작성자 제외)";
    }

    // 참가자는 생략 가능하다. 없으면 0명으로 보여준다 — 필드가 없다고 카드를 실패시킬 이유가 없다.
    private int participantCount(JsonNode body) {
        JsonNode participants = body.get("participantUserIds");
        return participants == null || !participants.isArray() ? 0 : participants.size();
    }
}
