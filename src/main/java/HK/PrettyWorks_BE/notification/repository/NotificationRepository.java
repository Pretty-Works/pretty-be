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

    // 뱃지. 30초마다 호출되므로 개수를 세지 않고 한 건이라도 있는지만 본다.
    // 파생 exists 쿼리는 setMaxResults(1)이 붙어 첫 행에서 멈춘다 — count(*)는 구간을 끝까지 훑는다.
    // 두 메서드로 나뉘는 이유: JPQL 파라미터로는 null 분기를 만들 수 없고,
    // (:lastSeenId is null or ...) 로 합치면 @Query가 되어 그 최적화를 잃는다.
    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndIdGreaterThan(Long userId, Long lastSeenId);

    // 뱃지를 끌 기준점. 알림이 하나도 없으면 null이다.
    @Query("select max(n.id) from NotificationEntity n where n.userId = :userId")
    Long findLatestId(@Param("userId") Long userId);

    // 개별 읽음: 남의 알림을 읽음 처리하지 못하도록 userId를 함께 건다.
    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

    // 보관 기간이 지난 알림을 정리한다. 삭제 건수를 반환.
    // 읽음 여부를 보지 않는다 — 안 읽은 채로 90일이 지났다면 이미 의미가 없는 알림이다.
    @Modifying
    @Query("delete from NotificationEntity n where n.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold);
}
