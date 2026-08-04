package HK.PrettyWorks_BE.notification.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 수신자. 알림 1건은 한 사람의 것이라 여러 명에게 보낼 때는 행을 사람 수만큼 만든다.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    // 발생 시점에 완성한 문구. 원본이 바뀌어도 그때의 기록이 남는다.
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 시간이 원인인 알림(마감 임박 등)은 행위자가 없어 null이다.
    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public NotificationEntity(Long userId, NotificationType type, String title, Long actorId,
                              NotificationTargetType targetType, Long targetId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public boolean isRead() {
        return readAt != null;
    }

    // 이미 읽었다면 최초 시각을 유지한다.
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }
}
