package HK.PrettyWorks_BE.user.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum UserErrorCode implements ErrorCode {
    INACTIVE_USER(HttpStatus.BAD_REQUEST, "USER_001", "현재 재직중이지 않은 사용자입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_002", "존재하지 않는 사용자입니다."),
    RESIGNED_USER(HttpStatus.BAD_REQUEST, "USER_003", "이미 퇴사한 사용자입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
