package HK.PrettyWorks_BE.project.finance.controller;

import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import HK.PrettyWorks_BE.project.finance.dto.res.ExpenseResponse;
import HK.PrettyWorks_BE.project.finance.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

// @Validated: @RequestHeader 등 메서드 파라미터의 제약(@Size)을 검증하려면 필요합니다. (없으면 조용히 무시됨)
@RestController
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;

    // 재무 화면 헤더 proj_select로 확정된 프로젝트에 지출 1건 등록. 사용자(spender)는 토큰에서 주입(대리 등록 없음).
    @Operation(summary = "프로젝트 지출 등록",
            description = "프로젝트 참여중 멤버가 본인 명의로 지출 1건 등록. spender는 토큰 userId로 서버가 채움. "
                    + "Idempotency-Key 헤더로 중복 등록을 방지할 수 있음")
    @PostMapping("/api/v1/projects/{projectId}/expenses")
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Parameter(description = "중복 등록 방지용 멱등 키(선택). 폼이 열릴 때 UUID v4를 발급해 두고, "
                    + "연타·재시도 시 같은 키를 재사용하면 첫 응답이 그대로 반환됩니다.",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @Size(max = 64, message = "Idempotency-Key는 64자 이하여야 합니다.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.create(projectId, userId, idempotencyKey, request);

        return ResponseEntity.ok(response);
    }

    // 본인이 등록한 지출 1건 수정 (5필드). projectId·spenderId는 변경 불가(바디에 와도 무시).
    @Operation(summary = "프로젝트 지출 수정",
            description = "본인이 등록한 지출만 수정 가능. 사용일·유형·사용처·목적·금액만 변경(프로젝트·사용자 불변)")
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
    @Operation(summary = "프로젝트 지출 삭제",
            description = "본인이 등록한 지출만 소프트 삭제. 이미 삭제된 건 재요청은 성공(멱등)")
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
