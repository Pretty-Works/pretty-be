package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
}
