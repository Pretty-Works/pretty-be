package HK.PrettyWorks_BE.project.finance.controller;

import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseStatus;
import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import HK.PrettyWorks_BE.project.finance.dto.res.BudgetSummaryResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseListResponse;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import HK.PrettyWorks_BE.project.finance.service.ExpenseService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 ExpenseApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
@RestController
@RequiredArgsConstructor
public class ExpenseController implements ExpenseApi {

    private final ExpenseService expenseService;

    // 재무 화면 헤더 proj_select로 확정된 프로젝트에 지출 1건 등록. 사용자(spender)는 토큰에서 주입(대리 등록 없음).
    @Override
    @PostMapping("/api/v1/projects/{projectId}/expenses")
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Parameter(description = "중복 등록 방지용 멱등 키(선택, 64자 이하). 폼이 열릴 때 UUID v4를 발급해 두고, "
                    + "연타·재시도 시 같은 키를 재사용하면 첫 응답이 그대로 반환됩니다.",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.create(projectId, userId, idempotencyKey, request);

        return ResponseEntity.ok(response);
    }

    // 재무 화면 '지출 내역' 테이블. 행을 클릭해 여는 수정 팝업이 이 응답을 그대로 초기값으로 쓴다(별도 상세 조회 없음).
    @Override
    @GetMapping("/api/v1/projects/{projectId}/expenses")
    public ResponseEntity<PageResponse<ExpenseListResponse>> getExpenses(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Parameter(description = "조회할 구분. EXECUTED(사용 내역) / PLANNED(예정)", example = "EXECUTED")
            @RequestParam(defaultValue = "EXECUTED") ExpenseStatus status,
            @Parameter(description = "사용처·사용 목적 부분 일치 검색어", example = "카카오")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지당 개수 (1~100)")
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<ExpenseListResponse> response =
                expenseService.getExpenses(projectId, userId, status, keyword, PageRequests.of(page, size));

        return ResponseEntity.ok(response);
    }

    // 재무 화면 상단 '예산 현황' 카드. 목록과 분리한 건 페이지마다 전체 합계를 다시 구하지 않기 위해서다.
    @Override
    @GetMapping("/api/v1/projects/{projectId}/budget")
    public ResponseEntity<BudgetSummaryResponse> getBudget(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId
    ) {
        BudgetSummaryResponse response = expenseService.getBudget(projectId, userId);

        return ResponseEntity.ok(response);
    }

    // 본인이 등록한 지출 1건 수정 (5필드). projectId·spenderId는 변경 불가(바디에 와도 무시).
    @Override
    @PutMapping("/api/v1/projects/{projectId}/expenses/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.update(projectId, expenseId, userId, request);

        return ResponseEntity.ok(response);
    }

    // 본인이 등록한 지출 1건 소프트 삭제. 이미 삭제된 건 재요청은 멱등 성공.
    @Override
    @DeleteMapping("/api/v1/projects/{projectId}/expenses/{expenseId}")
    public Void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @PathVariable Long expenseId
    ) {
        expenseService.delete(projectId, expenseId, userId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }
}
