package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipantEntity, Long> {
}
