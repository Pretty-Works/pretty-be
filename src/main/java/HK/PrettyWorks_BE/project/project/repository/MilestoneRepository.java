package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {
}
