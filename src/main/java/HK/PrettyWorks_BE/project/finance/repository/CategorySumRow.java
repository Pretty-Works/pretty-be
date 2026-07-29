package HK.PrettyWorks_BE.project.finance.repository;

import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;

// 예산 현황의 항목(카테고리)별 사용 합계 프로젝션.
// 부서별(DepartmentSumRow)과 구조가 같지만 record를 나눈다 — 키 타입을 문자열로 합치면
// ExpenseCategory.INFRA와 DepartmentType.INFRA처럼 이름이 겹치는 값이 있어 잘못 매핑돼도 예외조차 나지 않는다.
public record CategorySumRow(
        ExpenseCategory category,
        Long amount
) {
}
