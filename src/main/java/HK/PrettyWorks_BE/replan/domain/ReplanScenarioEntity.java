package HK.PrettyWorks_BE.replan.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.constant.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시나리오 한 개 = 사용자가 고를 수 있는 선택지 하나.
//
// **이 엔티티는 저장 후 절대 바뀌지 않는다. 수정 메서드를 만들지 말 것.**
//
// 보안 근거: 적용 요청의 본문은 {"scenarioType": "EXTEND"}뿐이라, 승인 토큰이 봉인하는 것은
// "어느 시나리오냐"까지다(AgentWriteExecutor는 요청 본문 해시를 대조한다).
// 실제 변경 내용인 operations는 그 해시 밖에 있으므로, 여기가 바뀔 수 있으면
// 사용자가 승인한 것과 다른 변경이 실행돼도 아무 검증에도 걸리지 않는다.
// 계획이 달라져야 한다면 이 행을 고치는 대신 새 재계획을 만든다.
@Entity
@Table(name = "replan_scenarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplanScenarioEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "replan_id", nullable = false)
    private Long replanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 20)
    private ReplanScenarioType scenarioType;

    // 사용자에게 보여줄 한 줄 설명. 에이전트가 쓴 문장이므로 승인 화면의 근거로 삼지 않는다 —
    // 승인 화면은 operations를 직접 읽어 실제 변경 내역을 그린다.
    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk", nullable = false, length = 10)
    private RiskLevel risk;

    // 변경 목록(JSON 배열). 정규화하지 않는 이유는 operation마다 필요한 필드가 다르고,
    // 조회가 언제나 "이 재계획의 이 시나리오" 단위라 개별 항목을 질의할 일이 없기 때문이다.
    @Column(name = "operations", nullable = false, columnDefinition = "TEXT")
    private String operations;

    @Builder
    public ReplanScenarioEntity(Long replanId, ReplanScenarioType scenarioType,
                                String summary, RiskLevel risk, String operations) {
        this.replanId = replanId;
        this.scenarioType = scenarioType;
        this.summary = summary;
        this.risk = risk;
        this.operations = operations;
    }
}
