package HK.PrettyWorks_BE.agent.interaction.preview;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 회의록은 틀려도 저장은 성공하고 내용만 사실과 달라진다. 일자와 참석자 수를 앞에 세워
// 사용자가 "어제가 맞나", "3명이 맞나"를 한눈에 보게 한다.
@Component
public class MeetingCreatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "meeting.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);

        return "회의록을 작성합니다."
                + "\n- 회의명: " + PreviewFields.requiredText(body, "title")
                + "\n- 일자: " + PreviewFields.requiredText(body, "meetingDate")
                + "\n- 참석자: " + PreviewFields.arraySize(body, "attendeeIds") + "명"
                + "\n- 장소: " + PreviewFields.optionalText(body, "location", "미지정")
                + "\n- 프로젝트 #" + PreviewFields.requiredNumber(body, "projectId");
    }
}
