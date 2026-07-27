package HK.PrettyWorks_BE.calendar.leave.constant;

public enum LeaveType {
    ANNUAL("연차"),
    SICK("병가");

    private String description;
    LeaveType(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
