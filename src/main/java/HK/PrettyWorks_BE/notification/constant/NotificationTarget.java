package HK.PrettyWorks_BE.notification.constant;

import java.time.LocalDate;

/**
 * 알림을 눌렀을 때 이동할 곳. 종류·식별자·부모 프로젝트·날짜를 한 덩어리로 묶는다.
 *
 * <p>값 4개를 publish 인자로 늘어놓지 않는 이유: 전부 nullable이고 타입이 겹쳐서
 * ({@code Long, Long}) 순서를 바꿔 넣어도 컴파일이 통과한다. 그러면 알림은 저장되는데
 * 엉뚱한 화면으로 가고, 저장된 뒤에는 무엇이 맞았는지 알 방법이 없다.
 * 팩토리로만 만들게 해서 "말이 되는 조합"만 생기도록 한다.
 */
public record NotificationTarget(
        NotificationTargetType type,
        Long id,
        Long projectId,
        LocalDate date
) {

    private static final NotificationTarget NONE = new NotificationTarget(null, null, null, null);

    /**
     * 프로젝트 상세. 마일스톤·지출·할 일처럼 단독 화면이 없는 것들이 쓴다.
     */
    public static NotificationTarget project(Long projectId) {
        return new NotificationTarget(NotificationTargetType.PROJECT, projectId, null, null);
    }

    /**
     * 게시글 상세. 경로가 /projects/{projectId}/posts/{postId} 라 부모 id도 함께 싣는다.
     */
    public static NotificationTarget post(Long projectId, Long postId) {
        return new NotificationTarget(NotificationTargetType.POST, postId, projectId, null);
    }

    /**
     * 회의록 상세. 게시글과 같은 이유로 부모 id를 함께 싣는다.
     */
    public static NotificationTarget meeting(Long projectId, Long meetingId) {
        return new NotificationTarget(NotificationTargetType.MEETING, meetingId, projectId, null);
    }

    /**
     * 일정 상세(모달). 참가자 추가·시간 변경처럼 그 일정을 열어야 하는 알림이 쓴다.
     */
    public static NotificationTarget schedule(Long scheduleId) {
        return new NotificationTarget(NotificationTargetType.SCHEDULE, scheduleId, null, null);
    }

    /**
     * 그 날짜의 캘린더. 일정 제외·삭제처럼 대상을 열 수 없을 때 쓴다.
     *
     * <p>type을 NULL로 두는 게 의도다 — 여는 리소스가 없고 날짜만 있다.
     */
    public static NotificationTarget date(LocalDate date) {
        return new NotificationTarget(null, null, null, date);
    }

    /**
     * 이동할 곳이 없음. 프로젝트에서 제외된 사람처럼 어디로 보내도 막히는 경우.
     */
    public static NotificationTarget none() {
        return NONE;
    }
}
