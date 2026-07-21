package HK.PrettyWorks_BE.project.project.constant;

public enum ProjectStatus {
    ONGOING("진행중"),
    HOLDING("보류"),
    DROPPED("중단"),
    DONE("완료"),
    ARCHIVED("보관");   // 소프트 삭제: 삭제 시 이 상태로 전환 (하드 딜리트 대신)

    private String description;
    ProjectStatus(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
