package HK.PrettyWorks_BE.calendar.leave.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum LeaveErrorCode implements ErrorCode {
    LEAVE_NOT_FOUND(HttpStatus.NOT_FOUND, "LEAVE_001", "존재하지 않는 휴가입니다."),
    NO_LEAVE_PERMISSION(HttpStatus.FORBIDDEN, "LEAVE_002", "본인이 신청한 휴가만 수정·취소할 수 있습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
