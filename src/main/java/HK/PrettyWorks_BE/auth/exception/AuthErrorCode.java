package HK.PrettyWorks_BE.auth.exception;

import  HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum AuthErrorCode implements ErrorCode {
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH_001", "이미 사용 중인 이메일입니다."),
    BUSINESS_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "AUTH_002", "이미 등록된 사업자번호입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_003", "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
