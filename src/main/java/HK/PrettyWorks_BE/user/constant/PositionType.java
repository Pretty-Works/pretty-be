package HK.PrettyWorks_BE.user.constant;

public enum PositionType {
    STAFF("사원", 10),
    SENIOR("선임", 20),
    PART_LEADER("파트장", 30),
    TEAM_LEADER("팀장", 40),
    EXECUTIVE("임원", 50),
    VICE_PRESIDENT("부사장", 60),
    PRESIDENT("사장", 70);

    private String description;
    // 직급 순위 (숫자가 클수록 상위). DB에는 저장하지 않고 권한 판정(팀장 이상 등)에만 사용합니다.
    private int level;

    PositionType(String description, int level) {
        this.description = description;
        this.level = level;
    }

    public String getDescription() {
        return description;
    }

    public int getLevel() {
        return level;
    }

}
