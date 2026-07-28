package HK.PrettyWorks_BE.task.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TaskErrorCode implements ErrorCode {
    // TASK_001(프로젝트를 찾을 수 없음)은 공용 PROJECT_004로 통합되어 제거됨.
    // TASK_002(프로젝트 접근 권한 없음)는 공용 MEMBER_001로 통합되어 제거됨. 나머지 코드의 의미가 바뀌지 않도록 번호는 재사용하지 않는다.
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_003", "할 일을 찾을 수 없습니다."),
    NO_EDIT_PERMISSION(HttpStatus.FORBIDDEN, "TASK_004", "할 일을 수정할 권한이 없습니다."),
    NO_DELETE_PERMISSION(HttpStatus.FORBIDDEN, "TASK_005", "할 일을 삭제할 권한이 없습니다."),
    // TASK_006(완료·보관 프로젝트 변경 불가)은 공용 PROJECT_020으로 통합되어 제거됨.
    DUE_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "TASK_007", "마감일이 프로젝트 기간을 벗어났습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
