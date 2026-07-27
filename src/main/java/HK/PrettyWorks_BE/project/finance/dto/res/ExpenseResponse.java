package HK.PrettyWorks_BE.project.finance.dto.res;

// 지출 등록·수정 공통 응답. 생성/수정된 식별자만 반환한다.
public record ExpenseResponse(
        Long expenseId
) {
}
