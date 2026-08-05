package HK.PrettyWorks_BE.notification.event;

import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

// 알림 발행 진입점. 업무 서비스는 수신자 후보만 넘기고 공통 제외 규칙은 여기서 건다.
// 발행 지점이 여러 도메인으로 흩어지면서, 서비스마다 같은 필터를 복사하면 한 곳만 빠져도
// 본인에게 알림이 가거나 퇴사자 목록에 쌓인다.
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    /**
     * 수신자 후보에서 행위자와 퇴사자를 제외하고 알림을 발행한다.
     * 남는 수신자가 없으면 이벤트를 만들지 않는다 — 참여자 변경 없이 이름만 고치는 수정처럼
     * 아무도 받지 않는 경우가 흔하다.
     *
     * @param titleArgs {@code type}의 문구 템플릿에 채울 인자. 문구를 호출부가 직접 넘기지 않게 해
     *                  type과 어긋난 문장이 저장되는 길을 막는다.
     */
    public void publish(NotificationType type,
                        Collection<Long> recipientCandidates,
                        Long actorId,
                        NotificationTargetType targetType,
                        Long targetId,
                        Object... titleArgs) {
        List<Long> recipientIds = filterRecipients(recipientCandidates, actorId);
        if (recipientIds.isEmpty()) {
            return;
        }

        eventPublisher.publishEvent(NotificationEvent.of(
                type, recipientIds, actorId, targetType, targetId, titleArgs));
    }

    // 본인 제외: 내가 한 행동은 나에게 알리지 않는다.
    // 퇴사자 제외: 로그인할 수 없어 알림을 확인할 방법이 없다.
    private List<Long> filterRecipients(Collection<Long> candidates, Long actorId) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Long> withoutActor = candidates.stream()
                .filter(id -> !Objects.equals(id, actorId))
                .distinct()
                .toList();
        if (withoutActor.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(withoutActor).stream()
                .filter(UserPolicy::isEmployed)
                .map(UserEntity::getId)
                .toList();
    }
}
