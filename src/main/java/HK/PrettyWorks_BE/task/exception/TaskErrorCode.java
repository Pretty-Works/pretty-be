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
    DUE_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "TASK_007", "마감일이 프로젝트 기간을 벗어났습니다."),

    // 403 — 남에게 배정하려면 그 프로젝트의 오너이거나 역할이 PM이어야 한다(ProjectPolicy.canUpdate).
    // 본인에게 만드는 것은 참여자 누구나 할 수 있으므로 MEMBER_001과는 다른 상황이다.
    NO_ASSIGN_PERMISSION(HttpStatus.FORBIDDEN, "TASK_008", "다른 사람에게 할 일을 배정할 권한이 없습니다."),

    // 400 — 배정 대상이 그 프로젝트의 참여중(ACTIVE) 멤버가 아니다.
    // MEMBER_001을 쓰지 않는 이유: 그건 "호출자가 멤버가 아님"이라 화면에 띄울 문구가 다르다.
    ASSIGNEE_NOT_MEMBER(HttpStatus.BAD_REQUEST, "TASK_009", "담당자가 프로젝트 참여자가 아닙니다."),

    // 400 — 개인 할 일에는 배정할 상대가 없다. 프로젝트가 없으면 배정 권한을 판정할 근거도 없다.
    PERSONAL_TASK_NOT_ASSIGNABLE(HttpStatus.BAD_REQUEST, "TASK_010", "개인 할 일은 다른 사람에게 배정할 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
