package HK.PrettyWorks_BE.notification.event;

import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;

import java.util.List;

// 알림 발행 이벤트. 종류마다 클래스를 만들면 알림이 늘어날 때마다 클래스가 같이 불어나므로
// 한 종류로 두고 type으로 구분한다. 진짜 도메인 이벤트가 필요해지면 그때 나눈다.
//
// recipientIds에서 행위자·퇴사자·탈퇴 멤버를 빼는 건 발행하는 쪽 책임이다.
// 리스너가 일괄로 빼면 본인에게도 보내야 하는 알림(보안 알림 등)을 만들 수 없다.
public record NotificationEvent(
        NotificationType type,
        List<Long> recipientIds,
        String title,
        Long actorId,
        NotificationTargetType targetType,
        Long targetId
) {

    // notifications.title 칼럼 길이. 넘기면 저장이 깨지면서 업무 트랜잭션까지 함께 롤백된다.
    private static final int MAX_TITLE_LENGTH = 200;

    // 문구를 호출부가 직접 넘기지 않고 type에서 뽑는다. 둘을 따로 받으면 엉뚱한 문구가 붙은
    // 알림을 만들 수 있고, 그 상태로 저장되면 나중에 고칠 방법이 없다(문구는 발생 시점 기록이라).
    public static NotificationEvent of(NotificationType type,
                                       List<Long> recipientIds,
                                       Long actorId,
                                       NotificationTargetType targetType,
                                       Long targetId,
                                       Object... titleArgs) {
        return new NotificationEvent(
                type, recipientIds, fitTitle(type.title(titleArgs)), actorId, targetType, targetId);
    }

    // 문구에 끼워 넣는 값(마일스톤 목표 200자, 프로젝트명 100자 등)이 길면 상한을 넘길 수 있다.
    // 알림 하나 때문에 마일스톤 완료 처리가 실패하면 안 되므로 여기서 자른다.
    private static String fitTitle(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }
}
