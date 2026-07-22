package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipantEntity, Long> {

    // 일정의 특정 역할 참가자들을 삭제한다. 참가자 교체 시 기존 PARTICIPANT 행만 정리(WRITER는 건드리지 않음)하는 용도.
    void deleteByScheduleIdAndRole(Long scheduleId, ParticipantRole role);
}
