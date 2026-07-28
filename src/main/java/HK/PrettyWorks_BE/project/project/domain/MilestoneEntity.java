package HK.PrettyWorks_BE.project.project.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // 완료 시각. NULL이면 미완료. 목표일과는 무관하며 완료 토글 API로만 채워진다.
    // 목표일이 지났다고 완료로 보면 지연된 마일스톤이 완료율에 섞여 지표가 실제 진척을 왜곡한다.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public MilestoneEntity(Long projectId, LocalDate targetDate, String goal) {
        this.projectId = projectId;
        this.targetDate = targetDate;
        this.goal = goal;
    }

    // 프로젝트 수정 시 기존 마일스톤 갱신. 완료 상태는 건드리지 않는다 —
    // 목표일을 미루거나 문구를 고친다고 이미 달성한 사실이 사라지지는 않기 때문이다.
    public void update(LocalDate targetDate, String goal) {
        this.targetDate = targetDate;
        this.goal = goal;
    }

    public boolean isDone() {
        return completedAt != null;
    }

    // 완료 토글: done=true면 미완료였을 때만 완료 시각 기록(멱등), done=false면 완료 취소.
    // 시각은 호출자가 넘긴다 — 엔티티가 시스템 시계를 직접 부르면 테스트에서 고정할 수 없다.
    public void toggleDone(boolean done, LocalDateTime now) {
        if (done) {
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.completedAt = null;
        }
    }

}
