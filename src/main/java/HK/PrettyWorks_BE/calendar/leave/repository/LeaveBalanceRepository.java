package HK.PrettyWorks_BE.calendar.leave.repository;

import HK.PrettyWorks_BE.calendar.leave.domain.LeaveBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalanceEntity, Long> {

    // 연차 현황 조회: 사용자·연도별 부여일수 1행(UNIQUE user_id+year). 아직 부여 전이면 비어 있음.
    Optional<LeaveBalanceEntity> findByUserIdAndYear(Long userId, Short year);
}
