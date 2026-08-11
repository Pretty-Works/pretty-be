package HK.PrettyWorks_BE.replan.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 시나리오 선택을 돕는 위험도. 서버는 이 값으로 아무것도 판정하지 않는다 —
// 에이전트가 붙인 라벨을 그대로 보관했다가 사용자에게 보여줄 뿐이다.
// 서버 판정에 쓰면 에이전트가 라벨만 LOW로 낮춰 검증을 우회할 수 있게 된다.
@Getter
@AllArgsConstructor
public enum RiskLevel {

    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음");

    private final String description;
}
