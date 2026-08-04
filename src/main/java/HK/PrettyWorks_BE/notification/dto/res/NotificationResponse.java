package HK.PrettyWorks_BE.notification.dto.res;

import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.domain.NotificationEntity;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        Actor actor,
        Target target,
        boolean read,
        LocalDateTime createdAt
) {

    // 시간이 원인인 알림(마감 임박 등)은 행위자가 없어 null로 내려간다.
    public record Actor(Long userId, String name) {
    }

    // 종류와 ID만 준다. /projects/2 같은 경로를 서버가 만들면 화면 구조가 바뀔 때마다 서버를 고쳐야 한다.
    public record Target(NotificationTargetType type, Long id) {
    }

    public static NotificationResponse of(NotificationEntity notification, String actorName) {
        Long actorId = notification.getActorId();
        NotificationTargetType targetType = notification.getTargetType();

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                actorId == null ? null : new Actor(actorId, actorName),
                targetType == null ? null : new Target(targetType, notification.getTargetId()),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
