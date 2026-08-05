package HK.PrettyWorks_BE.calendar.schedule.dto.res;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import lombok.Builder;

import java.time.LocalDateTime;

// 일정 수정 결과. 부분 수정이라 "무엇이 최종값이 됐는지"는 요청만 봐서는 알 수 없다
// (미전달 필드는 기존 값이 남고, allDay면 시각이 정규화된다).
// 병합된 최종값을 함께 돌려줘야 호출자가 다시 조회하지 않는다.
//
// 전부 서비스가 병합하면서 이미 손에 쥔 값이라 조회가 늘지 않는다.
// 참가자 수처럼 다시 세야 하는 값은 넣지 않는다 — 화면이 쓰지 않는데 모든 수정에 쿼리가 하나 붙는다.
@Builder
public record ScheduleUpdateResponse(
        Long scheduleId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        ScheduleType type
) {
}
