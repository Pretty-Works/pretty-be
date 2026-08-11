package HK.PrettyWorks_BE.calendar.leave.repository;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;

import java.time.LocalDateTime;

// 휴가 목록 JPQL 프로젝션. schedule_leaves(유형·사유·일수) + schedules(기간·신청자) + users(이름)를 한 번에 읽는다.
//
// 일정 전체를 훑고 휴가만 골라내는 방식이 아니라 schedule_leaves에서 시작한다.
// 1년치 조회에서 전자는 전사 일정 수천 건을 읽고 대부분 버리게 된다.
public record LeaveListRow(
        Long leaveId,
        Long scheduleId,
        LeaveType leaveType,
        String reason,
        Integer days,
        // 휴가는 종일 일정이라 00:00:00 ~ 23:59:59로 저장된다. 날짜만 필요하면 toLocalDate()로 되돌린다.
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long userId,
        String userName
) {
}
