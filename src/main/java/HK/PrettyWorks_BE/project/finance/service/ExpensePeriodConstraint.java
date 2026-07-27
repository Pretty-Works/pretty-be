package HK.PrettyWorks_BE.project.finance.service;

import HK.PrettyWorks_BE.project.finance.repository.ExpenseRepository;
import HK.PrettyWorks_BE.project.project.service.ProjectPeriodConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 프로젝트 기간 축소 시, 새 기간을 벗어나는 사용일의 지출이 남는지 확인한다. (소프트 삭제된 건은 제외)
@Component
@RequiredArgsConstructor
public class ExpensePeriodConstraint implements ProjectPeriodConstraint {

    private final ExpenseRepository expenseRepository;

    @Override
    public boolean hasDataOutsidePeriod(Long projectId, LocalDate startDate, LocalDate endDate) {
        return expenseRepository.existsOutsidePeriod(projectId, startDate, endDate);
    }
}
