package HK.PrettyWorks_BE.project.finance.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ExpenseErrorCode implements ErrorCode {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPENSE_001", "프로젝트를 찾을 수 없습니다."),
    NO_EXPENSE_PERMISSION(HttpStatus.FORBIDDEN, "EXPENSE_002", "해당 프로젝트에 지출을 등록할 권한이 없습니다."),
    EXPENSE_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "EXPENSE_003", "사용일이 프로젝트 기간을 벗어났습니다."),
    EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPENSE_004", "지출 내역을 찾을 수 없습니다."),
    NO_EXPENSE_EDIT_PERMISSION(HttpStatus.FORBIDDEN, "EXPENSE_005", "본인이 등록한 지출만 수정·삭제할 수 있습니다."),
    ALREADY_DELETED_EXPENSE(HttpStatus.CONFLICT, "EXPENSE_006", "이미 삭제된 지출입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
