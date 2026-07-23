package HK.PrettyWorks_BE.calendar.schedule.constant;

public enum ScheduleType {
    MEETING("회의"),
    FIELDWORK("외근"),
    PERSONAL("개인");

    private String description;
    ScheduleType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
