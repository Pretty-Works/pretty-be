package HK.PrettyWorks_BE.task.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TaskErrorCode implements ErrorCode {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_001", "프로젝트를 찾을 수 없습니다."),
    NO_ADD_PERMISSION(HttpStatus.FORBIDDEN, "TASK_002", "해당 프로젝트에 할 일을 추가할 권한이 없습니다."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_003", "할 일을 찾을 수 없습니다."),
    NO_EDIT_PERMISSION(HttpStatus.FORBIDDEN, "TASK_004", "할 일을 수정할 권한이 없습니다."),
    NO_DELETE_PERMISSION(HttpStatus.FORBIDDEN, "TASK_005", "할 일을 삭제할 권한이 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
