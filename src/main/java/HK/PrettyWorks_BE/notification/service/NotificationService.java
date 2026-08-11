package HK.PrettyWorks_BE.notification.service;

import HK.PrettyWorks_BE.global.base.CursorResponse;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.notification.domain.NotificationEntity;
import HK.PrettyWorks_BE.notification.dto.res.NotificationResponse;
import HK.PrettyWorks_BE.notification.event.NotificationEvent;
import HK.PrettyWorks_BE.notification.exception.NotificationErrorCode;
import HK.PrettyWorks_BE.notification.repository.NotificationRepository;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    // 수신자 1명당 1행을 만든다. 읽음 여부가 사람마다 달라 한 행을 공유할 수 없다.
    //
    // 호출부의 트랜잭션에 합류한다(REQUIRED). 알림만 따로 커밋하면 본 작업이 롤백됐을 때
    // "제외되었습니다" 알림만 남는다.
    @Transactional
    public void create(NotificationEvent event) {
        List<Long> recipientIds = event.recipientIds();
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }

        // 발행하는 쪽이 여러 목록을 합쳐 넘기면 같은 사람이 겹칠 수 있다. 겹치면 드롭다운에
        // 같은 알림이 두 번 뜬다.
        List<NotificationEntity> notifications = recipientIds.stream()
                .distinct()
                .map(recipientId -> NotificationEntity.builder()
                        .userId(recipientId)
                        .type(event.type())
                        .title(event.title())
                        .actorId(event.actorId())
                        .targetType(event.targetType())
                        .targetId(event.targetId())
                        .build())
                .toList();

        notificationRepository.saveAll(notifications);
    }

    // 종 아이콘 드롭다운 목록. 읽은 알림도 계속 보이고 화면이 안 읽은 것만 강조한다.
    @Transactional(readOnly = true)
    public CursorResponse<NotificationResponse, Long> getNotifications(Long userId, Long cursor, int size) {
        // 범위 검증만 재사용한다(REQUEST_001). 조회는 hasNext를 판정하려고 한 건 더 가져오므로
        // 여기서 만든 PageRequest는 쓰지 않는다 — size가 상한이면 size+1이 상한을 넘어 거부된다.
        PageRequests.of(0, size);

        List<NotificationEntity> fetched =
                notificationRepository.findPage(userId, cursor, PageRequest.ofSize(size + 1));
        boolean hasNext = fetched.size() > size;
        List<NotificationEntity> items = hasNext ? fetched.subList(0, size) : fetched;

        // 행마다 조회하면 N+1이라 한 번에 모아 온다. 행위자가 없는 알림이 섞이므로 걸러서 넘긴다.
        Set<Long> actorIds = items.stream()
                .map(NotificationEntity::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> actorNames = userService.getNameMap(actorIds);

        List<NotificationResponse> responses = items.stream()
                .map(n -> NotificationResponse.of(n, actorNames.get(n.getActorId())))
                .toList();
        // 커서는 마지막 항목의 id다. 목록이 비면 이어받을 지점이 없다.
        Long nextCursor = responses.isEmpty()
                ? null : responses.get(responses.size() - 1).notificationId();

        return new CursorResponse<>(responses, nextCursor, hasNext);
    }

    // 뱃지(빨간 점) 유무. 마지막으로 알림함을 연 뒤에 새로 온 게 있는지만 본다.
    @Transactional(readOnly = true)
    public boolean hasUnseen(Long userId) {
        Long lastSeenId = currentUserService.getCurrentUser(userId).getLastSeenNotificationId();

        return lastSeenId == null
                ? notificationRepository.existsByUserId(userId)
                : notificationRepository.existsByUserIdAndIdGreaterThan(userId, lastSeenId);
    }

    // 드롭다운을 열 때 뱃지를 끈다. 목록의 읽음 표시(read_at)는 건드리지 않는다 —
    // 여는 것과 항목을 처리하는 건 다른 행동이고, 여기서 전부 읽음으로 바꾸면
    // "안 읽은 것만 강조"가 드롭다운을 여는 순간 무의미해진다.
    @Transactional
    public void markSeen(Long userId) {
        Long latestId = notificationRepository.findLatestId(userId);

        currentUserService.getCurrentUser(userId).markNotificationsSeen(latestId);
    }

    // 항목 클릭. 남의 알림을 읽음 처리하지 못하도록 조회 단계에서 userId를 함께 건다.
    @Transactional
    public void read(Long userId, Long notificationId) {
        NotificationEntity notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> BaseException.type(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markRead(LocalDateTime.now());
    }
}
