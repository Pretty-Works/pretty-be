package HK.PrettyWorks_BE.replan.dto.res;

import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.constant.RiskLevel;
import lombok.Builder;

import java.util.List;

// 재계획 저장 결과. 이 시점에 프로젝트 데이터는 아직 하나도 바뀌지 않았다.
// 사용자가 선택할 수 있도록 저장된 선택지를 그대로 되돌려준다.
@Builder
public record ReplanCreateResponse(

        Long replanId,
        Long projectId,
        List<Scenario> scenarios
) {

    @Builder
    public record Scenario(
            ReplanScenarioType scenarioType,
            String summary,
            RiskLevel risk,
            // 이 시나리오가 바꿀 건수. 사용자가 "얼마나 큰 변경인가"를 가늠하는 값이다.
            int operationCount
    ) {
    }
}
