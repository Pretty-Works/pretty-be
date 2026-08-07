package HK.PrettyWorks_BE.replan.policy;

import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import org.springframework.util.StringUtils;

// 변경 항목이 실행 가능한 형태인지 판정한다. 순수 판정(값만 보고 boolean 반환, DB·throw 없음).
//
// 종류마다 필요한 필드가 다르고, 그 규칙이 저장 시점(잘못된 계획을 만들지 않기)과
// 적용 시점(보관된 JSON이 코드 변경으로 낡았을 수 있음) 양쪽에 필요하다. 한 곳에 모아 둔다.
public final class ReplanOperationPolicy {

    private ReplanOperationPolicy() {
    }

    /**
     * 종류별 필수 값이 모두 채워졌는지.
     *
     * <p>{@code from} 계열을 필수로 두는 이유: 계획 당시 값이 없으면 적용 직전에 대조할 것이 없어져,
     * 그 사이 누가 먼저 바꿨는지 알 수 없는 채로 덮어쓰게 된다. 할 일·마일스톤에는 낙관적 락이 없어
     * 이 대조가 유일한 방어다.
     *
     * <p>어떤 시나리오에 어떤 종류가 오는지는 제한하지 않는다 — 범위를 줄이면서 마감일도 함께 조정하는 것처럼
     * 섞이는 편이 자연스럽고, 시나리오 종류는 해결 전략의 분류일 뿐이기 때문이다.
     */
    public static boolean isComplete(ReplanOperation operation) {
        if (operation == null || operation.operation() == null) {
            return false;
        }

        return switch (operation.operation()) {
            case PROJECT_TARGET_DATE_CHANGE ->
                    operation.from() != null && operation.to() != null;
            case PROJECT_MEMBER_ADD ->
                    operation.memberId() != null;
            case MILESTONE_TARGET_DATE_CHANGE ->
                    operation.milestoneId() != null && operation.from() != null && operation.to() != null;
            case TASK_DUE_DATE_CHANGE ->
                    operation.taskId() != null && operation.from() != null && operation.to() != null;
            // 담당자(toAssigneeId)는 선택이다. 비우면 재계획을 적용한 사람이 담당한다(TaskService.resolveAssignee).
            // 담당자를 넘기는 재배치는 이 항목과 TASK_DELETE 조합으로 표현한다 — 화면과 같은 방식이다.
            case TASK_CREATE ->
                    StringUtils.hasText(operation.content()) && operation.to() != null;
            // 하드 삭제라 되돌릴 수 없다. 지울 대상이 계획 당시의 그것이 맞는지 확인할 근거를 반드시 요구한다.
            case TASK_DELETE ->
                    operation.taskId() != null && StringUtils.hasText(operation.expectedContent());
        };
    }
}
