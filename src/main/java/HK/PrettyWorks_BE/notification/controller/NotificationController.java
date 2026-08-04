package HK.PrettyWorks_BE.notification.controller;

import HK.PrettyWorks_BE.global.base.CursorResponse;
import HK.PrettyWorks_BE.notification.dto.res.NotificationResponse;
import HK.PrettyWorks_BE.notification.dto.res.UnseenResponse;
import HK.PrettyWorks_BE.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 NotificationApi 인터페이스에 있습니다.
// 파라미터 제약(@Min·@Max)을 붙이지 않는 이유는 ProjectController 주석 참고 — size 검증은 서비스가 합니다.
@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @GetMapping("/api/v1/notifications")
    public ResponseEntity<CursorResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorResponse<NotificationResponse> response =
                notificationService.getNotifications(userId, cursor, size);

        return ResponseEntity.ok(response);
    }

    // 30초마다 호출되는 뱃지 전용. 목록 API와 분리해야 매번 20건을 조회하지 않는다.
    @Override
    @GetMapping("/api/v1/notifications/unseen")
    public ResponseEntity<UnseenResponse> getUnseen(
            @AuthenticationPrincipal Long userId
    ) {
        boolean hasUnseen = notificationService.hasUnseen(userId);

        return ResponseEntity.ok(new UnseenResponse(hasUnseen));
    }

    @Override
    @PatchMapping("/api/v1/notifications/seen")
    public ResponseEntity<Void> markSeen(
            @AuthenticationPrincipal Long userId
    ) {
        notificationService.markSeen(userId);

        return ResponseEntity.ok().build();
    }

    @Override
    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public ResponseEntity<Void> read(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.read(userId, notificationId);

        return ResponseEntity.ok().build();
    }
}
