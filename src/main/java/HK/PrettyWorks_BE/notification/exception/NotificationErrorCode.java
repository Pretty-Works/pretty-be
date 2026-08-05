package HK.PrettyWorks_BE.notification.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum NotificationErrorCode implements ErrorCode {

    // 404 — 없는 알림과 남의 알림을 하나로 묶었다. 나누면 "그 번호가 존재하긴 하는지"를
    // 응답으로 알려주는 꼴이 된다. 화면에서 할 일도 "목록을 새로고침"으로 같다.
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "알림을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
