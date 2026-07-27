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
import java.time.temporal.ChronoUnit;

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

    // 낙관적 락 버전. Hibernate가 UPDATE에 "AND version = ?" 조건을 붙이고 값을 1 올립니다.
    // 오너와 PM이 동시에 수정할 때 나중 트랜잭션이 앞선 변경을 덮어쓰는 것을 막습니다. (수정은 애플리케이션이 건드리지 않음)
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    // 수정 API: 프로젝트 기본 정보를 갱신합니다. (상태·id는 이 API로 바꾸지 않음)
    public void update(String name, LocalDate startDate, LocalDate targetDate,
                       BigDecimal targetBudget, String description) {
        this.name = name;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.targetBudget = targetBudget;
        this.description = description;
    }

    // 상태 변경 API: 프로젝트 진행 상태만 변경합니다.
    public void changeStatus(ProjectStatus status) {
        this.status = status;
    }

    // 진행률(%)을 조회 시점의 날짜로 계산합니다. 저장하지 않는 파생값입니다.
    // 시작 전 0, 종료일 이후 100, 그 사이는 경과 비율(내림). 하루짜리 프로젝트(시작일=목표일)는 0으로 나누지 않도록 분기합니다.
    public int calculateProgress(LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(startDate, targetDate);
        if (totalDays <= 0) {
            return today.isBefore(startDate) ? 0 : 100;
        }

        long elapsedDays = ChronoUnit.DAYS.between(startDate, today);
        if (elapsedDays <= 0) {
            return 0;
        }
        if (elapsedDays >= totalDays) {
            return 100;
        }

        return (int) (elapsedDays * 100 / totalDays);
    }

}
