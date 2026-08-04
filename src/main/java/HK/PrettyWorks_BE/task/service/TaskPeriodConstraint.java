package HK.PrettyWorks_BE.task.service;

import HK.PrettyWorks_BE.project.project.service.ProjectPeriodConstraint;
import HK.PrettyWorks_BE.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 프로젝트 기간 축소 시, 새 기간을 벗어나는 마감일의 할 일이 남는지 확인한다.
@Component
@RequiredArgsConstructor
public class TaskPeriodConstraint implements ProjectPeriodConstraint {

    private final TaskRepository taskRepository;

    @Override
    public boolean hasDataOutsidePeriod(Long projectId, LocalDate startDate, LocalDate endDate) {
        return taskRepository.existsOutsidePeriod(projectId, startDate, endDate);
    }
}
