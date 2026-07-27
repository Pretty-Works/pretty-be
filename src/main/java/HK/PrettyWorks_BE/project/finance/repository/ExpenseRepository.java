package HK.PrettyWorks_BE.project.finance.repository;

import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    // 수정·삭제: 지출 조회(프로젝트 소속 확인 포함). 삭제된 건도 반환해 EXPENSE_006(이미 삭제)을 구분한다.
    Optional<ExpenseEntity> findByIdAndProjectId(Long id, Long projectId);

    // 프로젝트 기간 축소 검증: 새 기간을 벗어나는 사용일이 남는지 확인한다. (소프트 삭제된 건은 제외)
    @Query("select count(e) > 0 from ExpenseEntity e " +
            "where e.projectId = :projectId and e.deletedAt is null " +
            "and (e.expenseDate < :startDate or e.expenseDate > :endDate)")
    boolean existsOutsidePeriod(@Param("projectId") Long projectId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}
