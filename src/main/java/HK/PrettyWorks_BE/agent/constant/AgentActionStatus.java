package HK.PrettyWorks_BE.agent.constant;

// 에이전트가 제안한 후속 동작(action)의 승인 상태.
//
// 응답의 requiresApproval=true 인 action만 PENDING으로 저장된다. PENDING인 동안 그 스레드는
// 잠기고, 승인 대기 뱃지에 집계된다. 승인/취소로 확정되면 잠금이 풀린다.
//
// action.type(FILL_FORM·NAVIGATE·CHOICE…)과 혼동하지 말 것. type은 LLM팀이 정의하고 계속
// 늘어나므로 문자열로 저장만 하지만, 이 상태값은 서버가 소유하는 도메인 개념이라 enum이다.
public enum AgentActionStatus {
    PENDING("승인 대기"),
    APPROVED("승인"),
    CANCELLED("취소");

    private String description;
    AgentActionStatus(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

    // 승인 API가 받을 수 있는 값인지. PENDING은 시작 상태일 뿐 전이 대상이 아니다.
    // 요청 DTO를 이 enum으로 받되 PENDING이 들어오면 여기서 걸러낸다.
    public boolean isResolution() {
        return this == APPROVED || this == CANCELLED;
    }

}
