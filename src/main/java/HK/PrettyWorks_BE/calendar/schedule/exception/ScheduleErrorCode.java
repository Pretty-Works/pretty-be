package HK.PrettyWorks_BE.calendar.schedule.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ScheduleErrorCode implements ErrorCode {
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_001", "존재하지 않는 일정입니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "SCHEDULE_002", "일정 종료일시는 시작일시 이후여야 합니다."),
    NO_SCHEDULE_PERMISSION(HttpStatus.FORBIDDEN, "SCHEDULE_003", "본인이 작성한 일정만 수정·삭제할 수 있습니다."),
    INVALID_SEARCH_PERIOD(HttpStatus.BAD_REQUEST, "SCHEDULE_004", "조회 종료일은 시작일 이후여야 합니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "SCHEDULE_005", "작성자는 나갈 수 없습니다. 전체 삭제를 이용해 주세요."),
    NOT_A_PARTICIPANT(HttpStatus.NOT_FOUND, "SCHEDULE_006", "해당 일정의 참가자가 아닙니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
