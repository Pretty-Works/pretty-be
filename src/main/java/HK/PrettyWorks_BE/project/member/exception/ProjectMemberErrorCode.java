package HK.PrettyWorks_BE.project.member.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ProjectMemberErrorCode implements ErrorCode {
    NOT_A_MEMBER(HttpStatus.FORBIDDEN, "MEMBER_001", "해당 프로젝트에 참여하고 있지 않습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
