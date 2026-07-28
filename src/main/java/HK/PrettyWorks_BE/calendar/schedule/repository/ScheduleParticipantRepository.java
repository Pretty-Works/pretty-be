package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipantEntity, Long> {

    // 참가자 교체(diff): 특정 역할의 참가 행 전부(활성 + 나간 것 포함). 재활성화/신규 판정에 필요.
    List<ScheduleParticipantEntity> findByScheduleIdAndRole(Long scheduleId, ParticipantRole role);

    // 목록 조회: 여러 일정의 '활성' 참가자를 IN 절로 한 번에 가져온다(나간 사람 left_at IS NOT NULL은 제외).
    List<ScheduleParticipantEntity> findByScheduleIdInAndLeftAtIsNull(List<Long> scheduleIds);

    // 나가기: 내 활성 참가 행 조회. 없으면 참가자 아님(SCHEDULE_006).
    Optional<ScheduleParticipantEntity> findByScheduleIdAndUserIdAndLeftAtIsNull(Long scheduleId, Long userId);

    // 나가기: 나 말고 다른 활성 참가자가 있는지. 작성자 나가기 Block / '혼자' 판정에 사용.
    boolean existsByScheduleIdAndUserIdNotAndLeftAtIsNull(Long scheduleId, Long userId);
}
