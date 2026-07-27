package HK.PrettyWorks_BE.project.finance.repository;

import HK.PrettyWorks_BE.project.finance.domain.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    // 수정·삭제: 지출 조회(프로젝트 소속 확인 포함). 삭제된 건도 반환해 EXPENSE_006(이미 삭제)을 구분한다.
    Optional<ExpenseEntity> findByIdAndProjectId(Long id, Long projectId);
}
