package HK.PrettyWorks_BE.project.finance.controller;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseStatus;
import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import HK.PrettyWorks_BE.project.finance.dto.res.BudgetSummaryResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseListResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 재무 API의 Swagger 문서 전용 인터페이스. 컨트롤러(ExpenseController)가 implements 한다.
//
// 필수 여부·길이 제한·타입은 스키마에 이미 나오므로 적지 않는다.
// 코드 값의 의미, 모르면 사고가 나는 계약, 도메인 고유 에러만 남긴다.
// 성공 응답의 BaseResponse 래핑과 공통 401·500은 SwaggerConfig의 customizer가 붙인다.
@Tag(name = "재무", description = "프로젝트 지출 등록·수정·삭제·목록과 예산 현황 조회 API.")
public interface ExpenseApi {

    @Operation(
            summary = "지출 등록",
            description = """
                    참여중인 멤버가 **본인 명의로** 지출을 등록합니다. 지출자는 토큰에서 가져오므로 대리 등록은 없습니다.

                    [category] TRANSPORT 교통비 / MEAL 식대 / SOFTWARE 소프트웨어 / OFFICE_SUPPLY 사무용품 /
                    EDUCATION 교육·세미나 / LABOR 인건비 / OUTSOURCING 외주 / INFRA 인프라 / ETC 기타

                    [expenseDate] 프로젝트 기간 안이어야 합니다(EXPENSE_003). **미래 날짜도 기간 안이면 허용**되며
                    조회 시 '사용 예정'으로 분류됩니다.
                    [amount] 원 단위 정수, 1원 이상.

                    완료·보관된 프로젝트에도 등록할 수 있습니다 — 종료 후 정산되는 비용이 있기 때문입니다.
                    퇴사자는 등록할 수 없고(USER_003) 휴직자는 가능합니다.

                    Idempotency-Key 헤더에 UUID를 담아 보내면 연타·재시도해도 지출이 두 건 생기지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 사용일이 프로젝트 기간을 벗어남(EXPENSE_003) / 퇴사한 사용자(USER_003)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)"),
            @ApiResponse(responseCode = "409", description = "같은 멱등 키로 다른 내용의 요청이 접수됨(REQUEST_028)")
    })
    ResponseEntity<ExpenseResponse> create(Long userId, Long projectId, String idempotencyKey, ExpenseRequest request);

    @Operation(
            summary = "지출 목록 조회",
            description = """
                    참여중인 멤버가 팀 전체 지출을 페이지 단위로 조회합니다. 삭제된 지출은 나오지 않습니다.

                    [status] EXECUTED 사용 내역(기본) / PLANNED 사용 예정
                    - 저장된 값이 아니라 **사용일과 오늘을 비교해 파생**합니다. 사용일이 오늘 이전이면 EXECUTED입니다.
                    [keyword] 사용처·사용 목적을 함께 봅니다. 둘 중 하나라도 부분 일치하면 결과에 포함됩니다.
                    [정렬] 서버 고정 — 사용 내역은 최신순, 예정은 임박순으로 방향이 반대입니다.

                    수정 팝업은 **별도 상세 조회 없이 이 응답을 그대로** 초기값으로 씁니다(편집 대상 5개 필드가 모두 들어 있음).
                    spender.userId로 수정·삭제 버튼 노출을 판단하세요. 본인 지출만 수정할 수 있습니다.

                    ⚠️ 수정으로 사용일이 바뀌면 그 항목이 현재 탭에서 사라질 수 있습니다(EXECUTED ↔ PLANNED 전환).
                    저장 후 목록과 예산 현황을 함께 다시 조회하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과가 없으면 content는 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "잘못된 status 값 또는 페이지 파라미터 범위 위반(REQUEST_001)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님(MEMBER_001)"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)")
    })
    ResponseEntity<PageResponse<ExpenseListResponse>> getExpenses(
            Long userId, Long projectId, ExpenseStatus status, String keyword, int page, int size);

    @Operation(
            summary = "예산 현황 조회",
            description = """
                    참여중인 멤버가 프로젝트의 예산 집계를 조회합니다. 저장하지 않고 조회 시점에 계산하므로
                    지출을 등록·수정·삭제하면 즉시 반영됩니다.

                    [totalBudget] 할당 예산. 0은 '예산 제한 없음'이며 이때 executionRate는 0으로 응답합니다.
                    [remaining] 할당 − 사용 − 사용 예정. 예산 초과를 막지 않으므로 **음수가 될 수 있습니다.**
                    [byCategory·byDepartment] 항목별/부서별 토글이 서버 왕복 없이 전환되도록 둘을 함께 내려줍니다.
                    둘 다 **'사용' 기준**이며 사용 예정은 빠집니다. 금액 내림차순이고 사용액이 0인 항목은 나오지 않습니다.
                    [department] 지출자의 현재 소속에서 파생합니다. 담당자가 부서를 옮기면 집계도 따라 옮겨갑니다.

                    ⚠️ ratio는 각각 내림이라 **합이 100%에 못 미칠 수 있습니다.** 도넛에서 100%를 맞춰야 하면
                    가장 큰 항목에 잔차를 더하는 식으로 화면에서 처리하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (지출이 없으면 금액은 0, 두 배열은 빈 배열)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님(MEMBER_001)"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)")
    })
    ResponseEntity<BudgetSummaryResponse> getBudget(Long userId, Long projectId);

    @Operation(
            summary = "지출 수정",
            description = """
                    **본인이 등록한 지출만** 수정할 수 있습니다(EXPENSE_005). 프로젝트 오너라도 남의 지출은 수정할 수 없습니다.

                    사용일·유형·사용처·목적·금액 5개만 바뀝니다. 프로젝트와 지출자는 변경할 수 없으며 본문에 담아도 무시됩니다.
                    이미 삭제된 지출은 수정할 수 없습니다(EXPENSE_006).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 사용일이 프로젝트 기간을 벗어남(EXPENSE_003) / 퇴사한 사용자(USER_003)"),
            @ApiResponse(responseCode = "403", description = "본인이 등록한 지출이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "EXPENSE_005", "message": "본인이 등록한 지출만 수정·삭제할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "지출을 찾을 수 없거나 이 프로젝트의 지출이 아님(EXPENSE_004)"),
            @ApiResponse(responseCode = "409", description = "이미 삭제된 지출(EXPENSE_006)")
    })
    ResponseEntity<ExpenseResponse> update(Long userId, Long projectId, Long expenseId, ExpenseRequest request);

    @Operation(
            summary = "지출 삭제",
            description = """
                    **본인이 등록한 지출만** 삭제할 수 있습니다(EXPENSE_005).

                    소프트 삭제라 행은 남고 조회·집계에서만 빠집니다(감사 추적).
                    이미 삭제된 지출을 다시 삭제해도 성공합니다(멱등). result는 null입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공 (이미 삭제된 경우에도 성공)"),
            @ApiResponse(responseCode = "400", description = "퇴사한 사용자(USER_003)"),
            @ApiResponse(responseCode = "403", description = "본인이 등록한 지출이 아님(EXPENSE_005)"),
            @ApiResponse(responseCode = "404", description = "지출을 찾을 수 없거나 이 프로젝트의 지출이 아님(EXPENSE_004)")
    })
    Void delete(Long userId, Long projectId, Long expenseId);
}
