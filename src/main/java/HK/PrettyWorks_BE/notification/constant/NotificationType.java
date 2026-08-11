package HK.PrettyWorks_BE.notification.constant;

// 알림 종류. 화면은 이 코드로 아이콘·색을 고르고, 문구는 titleFormat으로 발생 시점에 완성해 저장한다.
public enum NotificationType {

    PROJECT_MEMBER_ADDED("'%s' 프로젝트에 참여자로 추가되었습니다"),
    PROJECT_MEMBER_REMOVED("'%s' 프로젝트에서 제외되었습니다"),
    PROJECT_STATUS_CHANGED("'%s' 프로젝트가 %s 상태로 변경되었습니다"),
    PROJECT_PERIOD_CHANGED("'%s' 프로젝트 기간이 %s ~ %s 로 변경되었습니다"),
    MILESTONE_COMPLETED("'%s' 마일스톤이 완료되었습니다"),
    EXPENSE_CREATED("'%s' 프로젝트에 %s원 지출이 등록되었습니다"),

    // 담당자와 작성자가 다를 때만 발행된다. 본인 할 일은 NotificationPublisher가 행위자를 걸러낸다.
    TASK_ASSIGNED("'%s' 할 일이 배정되었습니다 (마감 %s)"),
    TASK_DELETED("배정된 '%s' 할 일이 삭제되었습니다"),
    TASK_DUE_DATE_CHANGED("'%s' 할 일의 마감일이 %s 로 변경되었습니다");

    private final String titleFormat;

    NotificationType(String titleFormat) {
        this.titleFormat = titleFormat;
    }

    public String title(Object... args) {
        return String.format(titleFormat, args);
    }
}
