package HK.PrettyWorks_BE.replan.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 재계획 한 건. 여러 시나리오(ReplanScenarioEntity)를 묶는 상위 단위다.
//
// 계획 내용 자체는 이 엔티티에 없다. 시나리오 쪽에 있고 저장 후 절대 바뀌지 않는다.
// 여기서 변하는 것은 "적용했는가"라는 사실 기록뿐이다.
@Entity
@Table(name = "replans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplanEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // 재계획을 만든 사용자. 에이전트가 대신 만들었어도 그 대화의 주체를 남긴다.
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    // 왜 재계획이 필요했는지. 사용자가 나중에 "이건 왜 바뀐 거지"를 되짚는 근거다.
    @Column(name = "reason", length = 500)
    private String reason;

    // 적용된 시나리오. 아직 적용 전이면 null.
    // 중복 실행 자체는 승인 인터랙션(AgentInteractionEntity.executedAt)이 막는다 —
    // 이 필드는 방어가 아니라 감사 기록이다. 특히 할 일은 하드 삭제라 "무엇이 왜 사라졌는지"가 여기에만 남는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "applied_scenario_type", length = 20)
    private ReplanScenarioType appliedScenarioType;

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Builder
    public ReplanEntity(Long projectId, Long createdBy, String reason) {
        this.projectId = projectId;
        this.createdBy = createdBy;
        this.reason = reason;
    }

    public boolean isApplied() {
        return appliedAt != null;
    }

    // 적용 사실을 기록한다. 계획 내용(시나리오·operations)은 이 시점에도 건드리지 않는다 —
    // 승인한 내용과 실행된 내용이 같음을 나중에도 확인할 수 있어야 한다.
    public void markApplied(ReplanScenarioType scenarioType, Long actorId, LocalDateTime now) {
        this.appliedScenarioType = scenarioType;
        this.appliedBy = actorId;
        this.appliedAt = now;
    }
}
