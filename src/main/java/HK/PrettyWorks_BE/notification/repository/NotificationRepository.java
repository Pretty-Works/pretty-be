package HK.PrettyWorks_BE.notification.repository;

import HK.PrettyWorks_BE.notification.domain.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    // 목록 조회(커서). cursor가 null이면 첫 페이지, 있으면 그보다 오래된 것부터.
    // id는 AUTO_INCREMENT라 내림차순이 곧 최신순이고, 조회 중 새 알림이 쌓여도 경계가 밀리지 않는다.
    @Query("""
            select n from NotificationEntity n
            where n.userId = :userId
              and (:cursor is null or n.id < :cursor)
            order by n.id desc
            """)
    List<NotificationEntity> findPage(@Param("userId") Long userId,
                                      @Param("cursor") Long cursor,
                                      Pageable pageable);

    // 뱃지 카운트. 30초마다 호출되므로 목록을 읽지 않고 개수만 센다.
    long countByUserIdAndReadAtIsNull(Long userId);

    // 개별 읽음: 남의 알림을 읽음 처리하지 못하도록 userId를 함께 건다.
    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

    // 전체 읽음: 건수가 많을 수 있어 엔티티를 로드하지 않고 한 번의 UPDATE로 처리한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationEntity n
            set n.readAt = :now
            where n.userId = :userId and n.readAt is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
