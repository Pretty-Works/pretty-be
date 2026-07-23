package HK.PrettyWorks_BE.task.repository;

import HK.PrettyWorks_BE.task.domain.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
}
