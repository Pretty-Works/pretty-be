package HK.PrettyWorks_BE.user.constant;

public enum StatusType {
    ACTIVE("재직중"),
    ON_LEAVE("휴직"),
    RESIGNED("퇴사");

    private String description;
    StatusType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
