package HK.PrettyWorks_BE.calendar.schedule.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ScheduleErrorCode implements ErrorCode {
    // SCHEDULE_001(SCHEDULE_NOT_FOUND, 404)은 일정 수정/삭제 스텝에서 추가 예정 — 명세 예약 번호라 자리를 비워둡니다.
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "SCHEDULE_002", "일정 종료일시는 시작일시 이후여야 합니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
