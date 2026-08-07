package HK.PrettyWorks_BE.replan.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ReplanErrorCode implements ErrorCode {

    // 404 — 다른 프로젝트의 재계획을 지정한 경우도 이 코드로 응답한다.
    // "없음"과 "남의 것"을 구분하면 존재 여부가 새어나간다(PROJECT_022와 같은 판단).
    REPLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "REPLAN_001", "재계획을 찾을 수 없습니다."),

    SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "REPLAN_002", "선택한 시나리오를 찾을 수 없습니다."),

    // 403 — 재계획 적용은 프로젝트 계획을 다시 짜는 일이라 프로젝트 수정과 같은 권한을 요구한다
    // (오너이거나 프로젝트 내 역할이 PM인 참여자).
    NO_APPLY_PERMISSION(HttpStatus.FORBIDDEN, "REPLAN_003", "재계획을 적용할 권한이 없습니다."),

    // 409 — 계획을 세운 뒤 누군가 먼저 값을 바꿨다. 그대로 적용하면 남의 변경을 조용히 덮어쓴다.
    // 사용자는 재계획을 다시 만들어야 한다.
    SCENARIO_CONFLICT(HttpStatus.CONFLICT, "REPLAN_004", "계획을 세운 뒤 데이터가 변경되어 적용할 수 없습니다. 재계획을 다시 만들어 주세요."),

    // 400 — operation에 필요한 값이 없거나 이 시나리오에서 쓸 수 없는 종류다.
    INVALID_OPERATION(HttpStatus.BAD_REQUEST, "REPLAN_005", "재계획 변경 항목이 올바르지 않습니다."),

    // 409 — 이미 적용된 재계획. 승인 인터랙션이 같은 요청의 재실행을 막지만,
    // 다른 승인으로 같은 재계획을 다시 적용하려는 경우는 여기서 걸린다.
    ALREADY_APPLIED(HttpStatus.CONFLICT, "REPLAN_006", "이미 적용된 재계획입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
