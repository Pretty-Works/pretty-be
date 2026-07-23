package HK.PrettyWorks_BE.project.project.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "milestones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MilestoneEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "goal", nullable = false, length = 200)
    private String goal;

    @Builder
    public MilestoneEntity(Long projectId, LocalDate targetDate, String goal) {
        this.projectId = projectId;
        this.targetDate = targetDate;
        this.goal = goal;
    }

}
