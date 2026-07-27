package HK.PrettyWorks_BE.project.finance.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.project.finance.constant.ExpenseCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // 지출한 사용자 = 토큰 userId (대리 등록 없음). 목록 '사용자' 컬럼·부서별 집계 근거.
    @Column(name = "spender_id", nullable = false)
    private Long spenderId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ExpenseCategory category;

    @Column(name = "merchant", nullable = false, length = 100)
    private String merchant;

    @Column(name = "purpose", nullable = false, length = 255)
    private String purpose;

    @Column(name = "amount", nullable = false)
    private Long amount;

    // 소프트 삭제 — 삭제 API에서만 채운다. 등록 시엔 항상 null.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Builder
    public ExpenseEntity(Long projectId, Long spenderId, LocalDate expenseDate,
                         ExpenseCategory category, String merchant, String purpose, Long amount) {
        this.projectId = projectId;
        this.spenderId = spenderId;
        this.expenseDate = expenseDate;
        this.category = category;
        this.merchant = merchant;
        this.purpose = purpose;
        this.amount = amount;
    }

    // 수정 API: 등록과 동일한 5개 필드만 변경. projectId·spenderId는 불변(바디에 와도 무시).
    public void update(LocalDate expenseDate, ExpenseCategory category,
                       String merchant, String purpose, Long amount) {
        this.expenseDate = expenseDate;
        this.category = category;
        this.merchant = merchant;
        this.purpose = purpose;
        this.amount = amount;
    }

    // 삭제 API: 소프트 삭제 — 행은 남기고 deleted_at/deleted_by만 채운다(감사 추적). 본인만 삭제라 deleted_by = spender_id.
    public void softDelete(Long deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
