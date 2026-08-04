package HK.PrettyWorks_BE.notification.event;

import HK.PrettyWorks_BE.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 업무 서비스가 NotificationService를 직접 의존하지 않도록 한 겹 둔다.
//
// @TransactionalEventListener(AFTER_COMMIT)이 아니라 동기 @EventListener인 이유:
// AFTER_COMMIT은 트랜잭션 밖에서 돌아 알림 INSERT가 실패하면 재시도 없이 영구 유실된다.
// 알림도 같은 DB에 들어가 dual-write가 아니므로, 실패하면 본 작업과 함께 롤백되는 편이 낫다 —
// 프로젝트에서 제외됐는데 알림이 없으면 그 사람은 영영 모른다.
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void handle(NotificationEvent event) {
        notificationService.create(event);
    }
}
