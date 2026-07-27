package HK.PrettyWorks_BE.project.post.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PostErrorCode implements ErrorCode {
    AUTHOR_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_001", "작성자를 찾을 수 없습니다."),
//    ATTENDEE_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_002", "참석자를 찾을 수 없습니다."),
//    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_003", "존재하지 않는 프로젝트입니다."),
    DUPLICATED_MEETING(HttpStatus.CONFLICT, "MEETING_004", "동일한 문서번호의 회의록이 이미 존재합니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_005", "존재하지 않는 회의록입니다."),
    AUTHOR_IN_ATTENDEES(HttpStatus.BAD_REQUEST, "MEETING_006", "작성자는 참석자에 포함될 수 없습니다."),
    DUPLICATE_ATTENDEE(HttpStatus.BAD_REQUEST, "MEETING_007", "참석자가 중복되었습니다."),
    MEETING_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "MEETING_008", "회의 일자가 프로젝트 기간을 벗어났습니다."),
    NO_PERMISSION(HttpStatus.FORBIDDEN, "MEETING_009", "회의록에 대한 권한이 없습니다."),
    MEETING_NOT_IN_PROJECT(HttpStatus.NOT_FOUND, "MEETING_010", "해당 프로젝트의 회의록이 아닙니다.");


    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}