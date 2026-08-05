package HK.PrettyWorks_BE.agent.internal.dto.req;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import HK.PrettyWorks_BE.project.finance.dto.req.ExpenseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// expense.create 요청. 공개 API 바디(5필드)에 projectId를 더한 형태.
// 경로 변수를 바디에 싣는 이유는 AgentMeetingCreateRequest 주석 참고.
public record AgentExpenseCreateRequest(
        @NotNull Long projectId,
        @NotNull LocalDate expenseDate,
        @NotNull ExpenseCategory category,
        @NotBlank String merchant,
        @NotBlank String purpose,
        // 원 단위 정수. "12만원"은 120000이다.
        @NotNull @Min(1) Long amount
) {
    public ExpenseRequest toDomain() {
        return new ExpenseRequest(expenseDate, category, merchant, purpose, amount);
    }
}
