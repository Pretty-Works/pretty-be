package HK.PrettyWorks_BE.user.constant;

public enum GenderType {
    MALE("남성"),
    FEMALE("여성");

    private String description;
    GenderType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
