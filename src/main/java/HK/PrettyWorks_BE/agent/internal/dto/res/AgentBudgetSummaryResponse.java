package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.util.List;

// budget.summary — 목표 예산 대비 집행 현황(집계만).
//
// 소진 시점 예측은 서버가 하지 않는다. executionRate와 elapsedRate를 나란히 주고
// 에이전트가 판단하게 한다. 건별 내역이 필요하면 expense.list를 쓴다.
@Builder
public record AgentBudgetSummaryResponse(
        Long projectId,
        // 0이면 예산 제한 없음. 이때 remainingAmount·executionRate는 null이고,
        // "예산을 다 썼다"고 말하면 안 된다.
        Long targetBudget,
        Long spentAmount,
        Long remainingAmount,
        Integer executionRate,
        Integer elapsedRate,
        int expenseCount,
        List<CategoryAmount> byCategory
) {
    @Builder
    public record CategoryAmount(
            String category,
            String categoryLabel,
            Long amount,
            int share
    ) {
    }
}
