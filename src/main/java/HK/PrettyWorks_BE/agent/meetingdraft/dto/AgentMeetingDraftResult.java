package HK.PrettyWorks_BE.agent.meetingdraft.dto;

import java.util.List;

/**
 * FastAPI {@code POST /api/agent/meeting-draft} 응답을 그대로 담은 값.
 *
 * <p>여기까지가 "받은 것"이고, 규격 §4의 검증(참석자 명단 대조·날짜 형식·길이 제한)은
 * {@code MeetingDraftService}가 합니다. 클라이언트는 JSON을 자바 값으로 옮기기만 합니다.</p>
 *
 * <p>{@code meetingDate}가 {@link String}인 것은 의도입니다. 규격이 "형식이 올바르지 않으면
 * null"이라고 정한 이상, 형식 판정은 우리 몫입니다. 클라이언트에서 {@code LocalDate}로
 * 역직렬화해 버리면 잘못된 날짜가 파싱 예외(=502)가 되어, null로 내려보내야 할 값이
 * 초안 생성 전체를 실패시킵니다.</p>
 *
 * <p>근거 없는 필드는 모두 null일 수 있습니다. 전부 null인 응답도 정상입니다 —
 * "녹취록에서 아무것도 확정하지 못했다"는 뜻이고, 그때는 사용자가 빈 폼을 직접 채웁니다.</p>
 */
public record AgentMeetingDraftResult(
        String title,
        String meetingDate,
        String location,
        String purpose,
        String content,
        String followUp,
        // 원문 그대로. 명단 대조·중복 제거 전이라 우리 사용자가 아닌 id가 섞여 있을 수 있습니다.
        List<Long> attendeeUserIds
) {
}
