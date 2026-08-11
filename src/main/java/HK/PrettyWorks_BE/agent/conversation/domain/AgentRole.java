package HK.PrettyWorks_BE.agent.conversation.domain;

// 메시지 작성 주체. 한 행 = 말풍선 하나이며, 이 값으로 좌우 정렬과 스타일이 갈린다.
public enum AgentRole {
    USER("사용자"),
    AGENT("에이전트");

    private String description;
    AgentRole(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
