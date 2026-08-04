package HK.PrettyWorks_BE.project.finance.repository;

// 예산 현황의 사용/사용 예정 합계 프로젝션.
// 두 값이 같은 행 집합을 사용일 기준으로만 나눈 것이라 CASE 하나로 한 번에 집계한다(쿼리 2번 방지).
// 지출이 하나도 없으면 SUM이 null을 반환하므로 서비스에서 0으로 보정한다.
public record BudgetTotalRow(
        Long executed,
        Long planned
) {
}
