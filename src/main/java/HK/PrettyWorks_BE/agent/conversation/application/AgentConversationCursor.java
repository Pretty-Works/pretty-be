package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

// 대화 목록 스크롤의 기준점. 정렬이 (last_message_at desc, id desc)라 두 값을 함께 들고 다닌다.
//
// 알림처럼 id 하나로 끝내지 못하는 이유가 있다. last_message_at은 메시지가 오갈 때마다 바뀌므로,
// id만 받아 서버가 그 대화의 last_message_at을 다시 읽어 기준점을 만들면 — 스크롤 도중 그 대화에
// 답변이 도착해 맨 위로 올라간 순간 기준점도 함께 올라가 목록 전체가 다시 딸려 온다.
// 그래서 마지막 항목의 값을 응답할 때 그대로 얼려 두고, 다음 요청이 그 값을 되돌려 준다.
//
// 화면은 이 문자열을 열어 볼 필요가 없다. nextCursor를 받은 그대로 cursor에 실어 보내면 된다.
record AgentConversationCursor(LocalDateTime lastMessageAt, Long id) {

    // LocalDateTime.toString()에는 '_'가 없으므로 뒤에서부터 자르면 안전하게 갈린다.
    private static final String DELIMITER = "_";

    // 첫 페이지. 두 값이 null이면 쿼리의 커서 조건이 통째로 통과한다.
    static final AgentConversationCursor FIRST_PAGE = new AgentConversationCursor(null, null);

    // 우리가 내려준 커서만 받는다. 손댄 값은 조용히 첫 페이지로 되돌리지 않고 거부한다 —
    // 첫 페이지가 돌아오면 화면은 스크롤이 처음으로 되감긴 이유를 알 수 없다.
    static AgentConversationCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return FIRST_PAGE;
        }

        int delimiter = cursor.lastIndexOf(DELIMITER);
        if (delimiter < 0) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }

        try {
            return new AgentConversationCursor(
                    LocalDateTime.parse(cursor.substring(0, delimiter)),
                    Long.parseLong(cursor.substring(delimiter + 1)));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
        }
    }

    // 이번 페이지의 마지막 항목이 다음 페이지의 기준점이 된다.
    static String encode(AgentConversationEntity conversation) {
        return conversation.getLastMessageAt() + DELIMITER + conversation.getId();
    }
}
