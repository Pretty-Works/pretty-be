package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<ScheduleEntity, Long> {

    // 목록 조회: [fromStart, toEnd] 기간과 겹치고(overlap), 참가자에 userIds 중 한 명이라도 있는 일정을 startAt 오름차순으로 조회.
    // 겹침 = 일정 시작 <= 조회 끝  AND  일정 종료 >= 조회 시작.
    // 참가자 필터는 서브쿼리(schedule_participants에 userIds가 있는 scheduleId)로 처리한다.
    @Query("""
            SELECT s FROM ScheduleEntity s
            WHERE s.startAt <= :toEnd
              AND s.endAt >= :fromStart
              AND s.id IN (
                  SELECT p.scheduleId FROM ScheduleParticipantEntity p
                  WHERE p.userId IN :userIds
              )
            ORDER BY s.startAt ASC
            """)
    List<ScheduleEntity> findOverlappingByParticipants(
            @Param("fromStart") LocalDateTime fromStart,
            @Param("toEnd") LocalDateTime toEnd,
            @Param("userIds") List<Long> userIds
    );
}
