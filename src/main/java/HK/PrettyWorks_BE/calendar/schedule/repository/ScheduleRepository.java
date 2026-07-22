package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<ScheduleEntity, Long> {
}
