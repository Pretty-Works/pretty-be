package HK.PrettyWorks_BE.replan.constant;

// 재계획 크기 상한. 승인 한 번으로 바뀌는 범위를 제한한다.
//
// 사용자는 승인 화면의 요약을 보고 판단한다. 상한이 없으면 한 번의 "네"로 수백 건이 바뀔 수 있고,
// 그중에 할 일 하드 삭제가 섞여 있으면 되돌릴 방법이 없다.
public final class ReplanPolicy {

    // 한 재계획이 제시할 수 있는 시나리오 수. 사람이 한 화면에서 비교하고 고를 수 있는 정도.
    public static final int MAX_SCENARIOS = 5;

    // 한 시나리오가 담을 수 있는 변경 건수. 할 일 배치 생성 상한(TaskPolicy.MAX_CREATE_BATCH_SIZE)과 같은 값이다.
    public static final int MAX_OPERATIONS = 50;

    private ReplanPolicy() {
    }
}
