package HK.PrettyWorks_BE.replan.repository;

import HK.PrettyWorks_BE.replan.domain.ReplanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplanRepository extends JpaRepository<ReplanEntity, Long> {
}
