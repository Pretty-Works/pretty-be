package HK.PrettyWorks_BE.agent.tool.project.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// expense.list — 지출 건별 내역. 총액만 필요하면 budget.summary를 쓴다.
@Builder
public record AgentExpenseListResponse(
        List<AgentExpense> expenses,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentExpense(
            Long expenseId,
            LocalDate expenseDate,
            // 유형은 코드와 한글명을 함께 준다. 다시 요청에 실어야 하는 값이라 코드가 필요하고(expense.create),
            // 답변에 그대로 쓰려면 한글명이 필요하다.
            String category,
            String categoryLabel,
            String merchant,
            String purpose,
            Long amount,
            String spenderName,
            // 본인이 등록한 것만 수정 가능(EXPENSE_005). v1엔 수정 도구가 없어
            // FILL_FORM으로 안내할지 판단하는 데 쓴다.
            boolean canEdit
    ) {
    }
}
