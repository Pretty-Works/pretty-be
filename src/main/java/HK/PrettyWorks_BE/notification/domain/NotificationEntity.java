package HK.PrettyWorks_BE.notification.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.notification.constant.NotificationTarget;
import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    // 게시글·회의록처럼 상세 경로가 중첩(/projects/{projectId}/posts/{postId})인 대상의 부모 id.
    // 프로젝트 자체가 대상이면 null이다 — 그때는 targetId가 곧 projectId다.
    @Column(name = "target_project_id")
    private Long targetProjectId;

    // 특정 리소스가 아니라 날짜로 보내는 알림(일정 제외·삭제)이 쓴다. 화면이 날짜만 쓰므로 LocalDate.
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // target은 값 4개를 따로 받지 않고 NotificationTarget으로 묶어 받는다 — 전부 nullable에
    // 타입까지 겹쳐서, 따로 받으면 순서를 바꿔 넣어도 컴파일이 통과한다.
    @Builder
    public NotificationEntity(Long userId, NotificationType type, String title, Long actorId,
                              NotificationTarget target) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.actorId = actorId;

        NotificationTarget resolved = target == null ? NotificationTarget.none() : target;
        this.targetType = resolved.type();
        this.targetId = resolved.id();
        this.targetProjectId = resolved.projectId();
        this.targetDate = resolved.date();
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
