package HK.PrettyWorks_BE.user.constant;

public enum PositionType {
    STAFF("사원"),
    SENIOR("선임"),
    PART_LEADER("파트장"),
    TEAM_LEADER("팀장"),
    EXECUTIVE("임원"),
    VICE_PRESIDENT("부사장"),
    PRESIDENT("사장");

    private String description;
    PositionType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
