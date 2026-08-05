package HK.PrettyWorks_BE.calendar.leave.repository;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.domain.ScheduleLeaveEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleLeaveRepository extends JpaRepository<ScheduleLeaveEntity, Long> {

    // 일정 PATCH 차단용: 이 일정이 '휴가 일정'인지(= schedule_leaves 행 존재) 확인.
    boolean existsByScheduleId(Long scheduleId);

    // 목록 조회용: 결과 일정들 중 휴가인 것들을 IN 절로 한 번에 가져와 isLeave·leaveType 계산에 쓴다.
    List<ScheduleLeaveEntity> findByScheduleIdIn(List<Long> scheduleIds);

    // 휴가 목록 조회: 기간과 겹치는 대상자들의 휴가를 시작일 오름차순으로.
    //
    // schedules를 훑어 isLeave로 거르지 않고 schedule_leaves에서 시작한다 —
    // 1년치 20명이면 전사 일정 수천 건을 읽고 대부분 버리게 된다.
    // 휴가의 주인은 연결된 일정의 작성자(s.userId)다. 휴가엔 별도 신청자 컬럼이 없다.
    //
    // 보조 정렬(l.id)이 없으면 같은 날 시작하는 휴가들의 순서가 조회마다 달라져
    // 상한에 걸렸을 때 어떤 건이 잘릴지 예측할 수 없다.
    @Query("""
            SELECT new HK.PrettyWorks_BE.calendar.leave.repository.LeaveListRow(
                       l.id, s.id, l.leaveType, l.reason, l.days, s.startAt, s.endAt, s.userId, u.name)
            FROM ScheduleLeaveEntity l, ScheduleEntity s, UserEntity u
            WHERE l.scheduleId = s.id
              AND u.id = s.userId
              AND s.userId IN :userIds
              AND s.startAt <= :toEnd
              AND s.endAt >= :fromStart
              AND (:leaveType IS NULL OR l.leaveType = :leaveType)
            ORDER BY s.startAt ASC, l.id ASC
            """)
    List<LeaveListRow> findLeaves(@Param("fromStart") LocalDateTime fromStart,
                                  @Param("toEnd") LocalDateTime toEnd,
                                  @Param("userIds") List<Long> userIds,
                                  @Param("leaveType") LeaveType leaveType,
                                  Pageable pageable);

    // 연차 현황 '사용일수' 집계: 해당 유저의 그 해 특정 유형(ANNUAL) 휴가 days 합계.
    // schedule_leaves엔 user_id·날짜가 없어 schedules와 조인. 귀속 연도는 휴가 시작일(startAt) 기준 [yearStart, yearEnd).
    // 공가(EXCUSED)은 leaveType 파라미터로 걸러 제외. 해당 없으면 COALESCE로 0.
    @Query("""
            SELECT COALESCE(SUM(l.days), 0)
            FROM ScheduleLeaveEntity l, ScheduleEntity s
            WHERE l.scheduleId = s.id
              AND s.userId = :userId
              AND l.leaveType = :leaveType
              AND s.startAt >= :yearStart
              AND s.startAt < :yearEnd
            """)
    long sumUsedDays(@Param("userId") Long userId,
                     @Param("leaveType") LeaveType leaveType,
                     @Param("yearStart") LocalDateTime yearStart,
                     @Param("yearEnd") LocalDateTime yearEnd);
}
