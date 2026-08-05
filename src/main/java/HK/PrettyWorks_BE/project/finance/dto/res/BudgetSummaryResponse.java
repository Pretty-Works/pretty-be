package HK.PrettyWorks_BE.project.finance.dto.res;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import lombok.Builder;

import java.util.List;

// 예산 현황 조회 응답. 재무 화면 상단 '예산 현황' 카드(4지표 + 진행바 + 항목별/부서별 도넛)를 채운다.
//
// 어떤 값도 저장하지 않고 조회 시점에 계산한다. 지출이 등록·수정·삭제되면 즉시 소급 반영되며,
// 특정 시점의 숫자를 확정·동결하는 재무 마감 개념은 두지 않는다.
// 금액은 모두 원 단위 정수(Long) — targetBudget과 amount의 타입을 맞춰 변환 없이 계산한다.
@Builder
public record BudgetSummaryResponse(
        // 할당 예산. 0은 '예산 제한 없음'.
        Long totalBudget,
        // 사용 (사용일 <= 오늘)
        Long executed,
        // 사용 예정 (사용일 > 오늘)
        Long planned,
        // 할당 - 사용 - 사용 예정. 예산 초과를 차단하지 않으므로 음수가 될 수 있고, 경고 표시는 화면단이 판단한다.
        Long remaining,
        // 집행률(%) 내림. totalBudget이 0이면 나눌 수 없어 0으로 응답한다.
        int executionRate,
        // 프로젝트 기간 경과율(%) 내림, 0~100. 집행률과 나란히 보면 소진 속도를 읽을 수 있다
        // (경과 40%인데 집행 62%면 예정보다 빨리 쓰고 있다). 판단은 하지 않고 재료만 준다.
        int elapsedRate,
        // 집계에 포함된 지출 건수(소프트 삭제 제외).
        long expenseCount,
        // 항목별·부서별을 한 번에 내려준다. 화면의 항목별/부서별 토글이 서버 왕복 없이 즉시 전환되어야 하고,
        // group by 두 번의 비용이 왕복 한 번보다 훨씬 작다. 둘 다 '사용' 기준(사용 예정 제외).
        List<CategoryAmount> byCategory,
        List<DepartmentAmount> byDepartment
) {
    // ratio는 각각 내림이라 합이 100에 못 미칠 수 있다. 도넛에서 100%를 맞춰야 하면
    // 가장 큰 항목에 잔차를 더하는 식으로 화면이 처리한다 — 서버가 특정 항목을 부풀리면 금액과 어긋난다.
    @Builder
    public record CategoryAmount(
            ExpenseCategory category,
            Long amount,
            int ratio
    ) {
    }

    @Builder
    public record DepartmentAmount(
            DepartmentType department,
            Long amount,
            int ratio
    ) {
    }
}
