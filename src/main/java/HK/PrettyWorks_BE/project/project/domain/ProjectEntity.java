package HK.PrettyWorks_BE.project.project.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    // 금액이므로 오차 없는 BigDecimal 사용 (DECIMAL(15,2)와 매핑). 필수 입력이며 0은 '예산 제한 없음'을 의미합니다.
    @Column(name = "target_budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetBudget;

    @Column(name = "description", length = 500)
    private String description;

    @Builder
    public ProjectEntity(String name, ProjectStatus status, LocalDate startDate,
                         LocalDate targetDate, BigDecimal targetBudget, String description) {
        this.name = name;
        this.status = status;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.targetBudget = targetBudget;
        this.description = description;
    }

}
