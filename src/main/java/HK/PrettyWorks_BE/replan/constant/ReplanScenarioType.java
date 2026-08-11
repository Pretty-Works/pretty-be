package HK.PrettyWorks_BE.replan.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 재계획 시나리오 분류. "어떤 전략으로 문제를 풀 것인가"만 나타내고, 실제 변경 내용은 operations가 담는다.
//
// 분류와 변경 내용을 나눠 두는 이유: 한 시나리오가 여러 종류의 변경을 함께 담는다.
// 인력 재배치는 참여자 추가와 할 일 이관(삭제+생성)을 동시에 하고, 일정 조정은 프로젝트·마일스톤·할 일을 함께 옮긴다.
// 분류가 변경 내용을 결정하게 만들면 시나리오 종류가 늘 때마다 실행 코드가 복사된다.
@Getter
@AllArgsConstructor
public enum ReplanScenarioType {

    REALLOCATE("인력 재배치"),
    EXTEND("일정 조정"),
    REDUCE_SCOPE("범위 축소");

    private final String description;
}
