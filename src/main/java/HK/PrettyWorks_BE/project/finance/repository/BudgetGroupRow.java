package HK.PrettyWorks_BE.project.finance.repository;

// 예산 현황의 묶음별 집계 프로젝션. 항목별(카테고리)·부서별이 "묶음 키 + 금액"으로 구조가 같아 하나를 공유한다.
//
// key를 enum이 아니라 String으로 받는 이유: 두 집계의 키 타입이 서로 다른데(ExpenseCategory / DepartmentType)
// JPQL 생성자 표현식은 제네릭 타입 인자를 해석하지 못한다. 문자열로 받아 서비스에서 각자의 enum으로 되돌린다.
public record BudgetGroupRow(
        String key,
        Long amount
) {
}
