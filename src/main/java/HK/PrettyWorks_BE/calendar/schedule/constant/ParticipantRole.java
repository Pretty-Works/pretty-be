package HK.PrettyWorks_BE.calendar.schedule.constant;

public enum ParticipantRole {
    WRITER("작성자"),
    PARTICIPANT("참가자");

    private String description;
    ParticipantRole(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
