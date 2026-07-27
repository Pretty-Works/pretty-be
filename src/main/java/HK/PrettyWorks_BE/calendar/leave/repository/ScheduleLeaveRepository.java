package HK.PrettyWorks_BE.calendar.leave.repository;

import HK.PrettyWorks_BE.calendar.leave.domain.ScheduleLeaveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleLeaveRepository extends JpaRepository<ScheduleLeaveEntity, Long> {

    // 일정 PATCH 차단용: 이 일정이 '휴가 일정'인지(= schedule_leaves 행 존재) 확인.
    boolean existsByScheduleId(Long scheduleId);

    // 목록 조회용: 결과 일정들 중 휴가인 것들을 IN 절로 한 번에 가져와 isLeave·leaveType 계산에 쓴다.
    List<ScheduleLeaveEntity> findByScheduleIdIn(List<Long> scheduleIds);
}
