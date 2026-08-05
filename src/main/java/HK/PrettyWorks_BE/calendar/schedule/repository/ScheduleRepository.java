package HK.PrettyWorks_BE.calendar.schedule.repository;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<ScheduleEntity, Long> {

    // 목록 조회: [fromStart, toEnd] 기간과 겹치고(overlap), 참가자에 userIds 중 한 명이라도 있는 일정을 startAt 오름차순으로 조회.
    // 겹침 = 일정 시작 <= 조회 끝  AND  일정 종료 >= 조회 시작.
    // 참가자 필터는 서브쿼리(schedule_participants에 userIds가 있는 scheduleId)로 처리한다.
    //
    // type은 선택 필터, pageable은 건수 상한이다. 화면(캘린더)은 그 기간 일정을 전부 그려야 해서
    // 둘 다 비우고 부르지만, 결과를 반드시 좁혀야 하는 호출자(에이전트 도구)는 DB에서 잘라야 한다 —
    // 전부 읽어 메모리에서 자르면 뒤따르는 참가자·이름 조회까지 버릴 행을 위해 돈다.
    //
    // 보조 정렬(id)이 없으면 시작일시가 같은 일정들의 순서가 매 조회마다 달라져
    // 상한에 걸렸을 때 어떤 건이 잘릴지 예측할 수 없다.
    @Query("""
            SELECT s FROM ScheduleEntity s
            WHERE s.startAt <= :toEnd
              AND s.endAt >= :fromStart
              AND (:type IS NULL OR s.type = :type)
              AND s.id IN (
                  SELECT p.scheduleId FROM ScheduleParticipantEntity p
                  WHERE p.userId IN :userIds AND p.leftAt IS NULL
              )
            ORDER BY s.startAt ASC, s.id ASC
            """)
    List<ScheduleEntity> findOverlappingByParticipants(
            @Param("fromStart") LocalDateTime fromStart,
            @Param("toEnd") LocalDateTime toEnd,
            @Param("userIds") List<Long> userIds,
            @Param("type") ScheduleType type,
            Pageable pageable
    );
}
