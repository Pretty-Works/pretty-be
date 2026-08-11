package HK.PrettyWorks_BE.notification.constant;

// 알림 종류. 화면은 이 코드로 아이콘·색을 고르고, 문구는 titleFormat으로 발생 시점에 완성해 저장한다.
public enum NotificationType {

    PROJECT_MEMBER_ADDED("'%s' 프로젝트에 참여자로 추가되었습니다"),

    // target이 null이다 — 제외된 사람은 그 프로젝트 상세로 못 들어간다(참여중 멤버만, MEMBER_001).
    PROJECT_MEMBER_REMOVED("'%s' 프로젝트에서 제외되었습니다"),
    PROJECT_STATUS_CHANGED("'%s' 프로젝트가 %s 상태로 변경되었습니다"),
    PROJECT_PERIOD_CHANGED("'%s' 프로젝트 기간이 %s ~ %s 로 변경되었습니다"),
    MILESTONE_COMPLETED("'%s' 마일스톤이 완료되었습니다"),
    EXPENSE_CREATED("'%s' 프로젝트에 %s원 지출이 등록되었습니다"),

    // 담당자와 작성자가 다를 때만 발행된다. 본인 할 일은 NotificationPublisher가 행위자를 걸러낸다.
    // 게시글·회의록과 같은 규칙을 따른다 — 첫 인자는 프로젝트명, 할 일 내용은 콜론 뒤.
    // 화면이 맨 앞 따옴표 안의 값을 굵은 배지로 뽑아 쓰는데, 클릭 시 이동하는 곳이 프로젝트라 앞자리도 프로젝트명이어야 한다.
    TASK_ASSIGNED("'%s' 프로젝트에 할 일이 배정되었습니다: %s"),
    TASK_DELETED("'%s' 프로젝트에서 할 일이 삭제되었습니다: %s"),
    TASK_DUE_DATE_CHANGED("'%s' 프로젝트의 할 일 마감일이 %s 로 변경되었습니다: %s"),

    // HIGH 우선순위 게시글에만 발행된다(전체 발행하면 스팸이라 팀 결정으로 제한).
    POST_CREATED("'%s' 프로젝트에 중요 게시글이 등록되었습니다: %s"),
    POST_UPDATED("'%s' 게시글이 수정되었습니다"),

    // 참석자 + 프로젝트 관리자(오너·PM)가 수신 후보. 행위자 본인은 NotificationPublisher가 걸러낸다.
    // 회의록 '수정' 알림(MEETING_UPDATED)은 폐기했다 — 오타 하나만 고쳐도 참석자 전원이 받아 시끄러웠다.
    // 상수까지 지웠으므로 다시 추가하지 말 것. type이 EnumType.STRING이라 DB에 없는 이름이 남으면
    // 그 행을 읽는 순간 No enum constant 예외로 해당 사용자의 알림 목록 조회가 통째로 500이 된다.
    MEETING_CREATED("'%s' 프로젝트에 회의록이 등록되었습니다: %s"),

    // 일정 알림만 target이 SCHEDULE(id=scheduleId)이다. 일정은 프로젝트에 속하지 않는다.
    SCHEDULE_PARTICIPANT_ADDED("'%s' 일정에 참가자로 추가되었습니다"),
    SCHEDULE_PARTICIPANT_REMOVED("'%s' 일정에서 제외되었습니다"),
    SCHEDULE_TIME_CHANGED("'%s' 일정 시간이 %s ~ %s 로 변경되었습니다"),

    // PROJECT_MEMBER_REMOVED와 함께 target이 null인 알림 — 일정이 이미 삭제돼 열 수 있는 화면이 없다.
    // SCHEDULE로 id를 실어 보내면 화면이 없는 일정을 열려다 실패한다. 문구가 알림의 전체 내용이다.
    SCHEDULE_DELETED("'%s' 일정이 삭제되었습니다");

    private final String titleFormat;

    NotificationType(String titleFormat) {
        this.titleFormat = titleFormat;
    }

    public String title(Object... args) {
        return String.format(titleFormat, args);
    }
}
