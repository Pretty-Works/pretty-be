package HK.PrettyWorks_BE.notification.dto.res;

import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.notification.domain.NotificationEntity;

import java.time.LocalDate;
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

    /**
     * 이동할 곳. 재료만 주고 /projects/2/posts/7 같은 경로 조립은 화면이 한다 —
     * 서버가 만들면 화면 구조가 바뀔 때마다 서버를 고쳐야 한다.
     *
     * <p>{@code projectId}는 게시글·회의록처럼 상세 경로가 중첩일 때만 채워진다.
     * {@code date}는 여는 리소스 없이 날짜로만 보내는 알림(일정 제외·삭제)이 쓰며,
     * 이때 {@code type}과 {@code id}는 null이다.
     */
    public record Target(NotificationTargetType type, Long id, Long projectId, LocalDate date) {
    }

    public static NotificationResponse of(NotificationEntity notification, String actorName) {
        Long actorId = notification.getActorId();
        NotificationTargetType targetType = notification.getTargetType();
        LocalDate targetDate = notification.getTargetDate();

        // 날짜만 있는 알림도 이동할 곳이 있는 것이라 target을 내려야 한다.
        // targetType만 보고 판정하면 일정 제외·삭제의 날짜가 화면에 도달하지 못한다.
        boolean movable = targetType != null || targetDate != null;

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                actorId == null ? null : new Actor(actorId, actorName),
                movable ? new Target(targetType, notification.getTargetId(),
                        notification.getTargetProjectId(), targetDate) : null,
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
