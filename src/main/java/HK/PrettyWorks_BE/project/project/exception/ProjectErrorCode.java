package HK.PrettyWorks_BE.project.project.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ProjectErrorCode implements ErrorCode {
    NO_CREATE_PERMISSION(HttpStatus.FORBIDDEN, "PROJECT_001", "프로젝트를 생성할 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_002", "참여자를 찾을 수 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "PROJECT_003", "프로젝트 종료일은 시작일 이후여야 합니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_004", "프로젝트를 찾을 수 없습니다."),
    NO_EDIT_PERMISSION(HttpStatus.FORBIDDEN, "PROJECT_005", "프로젝트를 수정할 권한이 없습니다."),
    MILESTONE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "PROJECT_015", "마일스톤 목표일이 프로젝트 기간을 벗어났습니다."),
    MILESTONE_INCOMPLETE(HttpStatus.BAD_REQUEST, "PROJECT_016", "마일스톤은 목표일과 목표 내용을 모두 입력해 주세요."),
    NO_STATUS_CHANGE_PERMISSION(HttpStatus.FORBIDDEN, "PROJECT_017", "프로젝트 상태를 변경할 권한이 없습니다."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "PROJECT_018", "유효하지 않은 프로젝트 상태 값입니다."),
    STATUS_NOT_REVERTIBLE(HttpStatus.CONFLICT, "PROJECT_019", "완료·삭제된 프로젝트는 되돌릴 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
