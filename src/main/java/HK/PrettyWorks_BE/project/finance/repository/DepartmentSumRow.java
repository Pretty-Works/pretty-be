package HK.PrettyWorks_BE.project.finance.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;

// 예산 현황의 부서별 사용 합계 프로젝션. 부서는 지출자의 현재 소속에서 파생한다.
public record DepartmentSumRow(
        DepartmentType department,
        Long amount
) {
}
