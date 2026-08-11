package HK.PrettyWorks_BE.agent.interaction.preview;

import HK.PrettyWorks_BE.global.util.InclusiveDays;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// 일수를 함께 보여준다. "8월 10일부터 3일"이 실제로 며칠까지인지는 날짜만 봐서는 헷갈리고,
// 연차 차감은 이 일수만큼 일어난다.
@Component
public class LeaveCreatePreviewRenderer implements ApprovalPreviewRenderer {

    @Override
    public String tool() {
        return "leave.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        String startDate = PreviewFields.requiredText(body, "startDate");
        String endDate = PreviewFields.requiredText(body, "endDate");

        return "휴가를 신청합니다."
                + "\n- 기간: " + startDate + " ~ " + endDate + days(startDate, endDate)
                + "\n- 유형: " + PreviewFields.requiredText(body, "leaveType")
                + "\n- 사유: " + PreviewFields.optionalText(body, "reason", "미기재");
    }

    // 날짜 형식이 어긋나면 일수만 생략한다. 카드 전체를 실패시키면 사용자는 이유를 알 수 없고,
    // 형식 오류는 어차피 저장 단계에서 걸린다.
    private String days(String startDate, String endDate) {
        try {
            return " (" + InclusiveDays.between(LocalDate.parse(startDate), LocalDate.parse(endDate)) + "일)";
        } catch (DateTimeParseException malformedDate) {
            return "";
        }
    }
}
