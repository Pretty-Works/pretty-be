package HK.PrettyWorks_BE.project.member.constant;

public enum ProjectMemberStatus {
    ACTIVE("참여중"),
    LEFT("탈퇴");

    private String description;
    ProjectMemberStatus(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
