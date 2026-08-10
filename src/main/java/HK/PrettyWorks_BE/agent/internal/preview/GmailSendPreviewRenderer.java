package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.service.ApprovalPreviewRenderer;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

// 메일 발송 승인 카드.
//
// 다른 렌더러와 성격이 다르다. 일정·지출은 잘못 저장해도 지우면 되지만 나간 메일은 못 주워담고
// 회사 명의로 남는다. 게다가 발송은 MCP 안에서 끝나 BE를 거치지 않아, 승인 토큰을 검사할 지점이
// 없다 — 다른 쓰기 도구처럼 "토큰 없으면 거부"로 강제할 수가 없다.
// 즉 이 카드는 서버가 거는 통제가 아니라 사용자가 눈으로 막는 마지막 방어선이다.
//
// 그래서 받는 사람은 개수로 줄이거나 앞부분만 보여주지 않고 전부 적는다. 본문은 틀려도
// 다시 보내면 되지만, 수신자가 틀리면 되돌릴 방법이 없다.
//
// (BE 내부 도구가 아닌데 이 패키지에 두는 이유: PreviewFields가 패키지 전용이고,
//  승인 카드 렌더러가 흩어지면 "이 도구는 카드가 있나"를 한곳에서 못 본다.)
@Component
public class GmailSendPreviewRenderer implements ApprovalPreviewRenderer {

    // 본문은 앞부분만. 카드는 한눈에 훑고 누르는 자리다.
    private static final int MAX_PREVIEW_BODY = 200;

    @Override
    public String tool() {
        return "gmail.send";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        String cc = recipients(body, "cc", false);

        return "메일을 보냅니다. 보내고 나면 취소할 수 없습니다."
                + "\n- 받는 사람: " + recipients(body, "to", true)
                + (cc.isEmpty() ? "" : "\n- 참조: " + cc)
                + "\n- 제목: " + PreviewFields.requiredText(body, "subject")
                + "\n- 내용: " + PreviewFields.shorten(
                        PreviewFields.requiredText(body, "body"), MAX_PREVIEW_BODY);
    }

    // 주소가 하나라도 모양이 어긋나면 카드를 그리지 않는다. "받는 사람: , 홍길동" 같은 카드를
    // 승인하게 두면 승인을 받는 의미가 없다.
    private String recipients(JsonNode params, String field, boolean required) {
        JsonNode value = params.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            return "";
        }
        if (!value.isArray() || (required && value.size() == 0)) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        List<String> addresses = new ArrayList<>();
        for (JsonNode address : value) {
            if (!address.isTextual() || address.textValue().isBlank()) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            addresses.add(address.textValue().trim());
        }
        return String.join(", ", addresses);
    }
}
