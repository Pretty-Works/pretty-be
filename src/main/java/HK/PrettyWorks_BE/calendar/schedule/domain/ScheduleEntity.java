package HK.PrettyWorks_BE.calendar.schedule.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 작성자(users FK). 휴가도 schedules 행으로 저장되며, 이 값이 작성자 = WRITER 참가자입니다.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    // 종일 일정이면 true. true일 때 시간은 00:00:00~23:59:59로 정규화해 저장합니다.
    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Builder
    public ScheduleEntity(Long userId, String title, LocalDateTime startAt,
                          LocalDateTime endAt, boolean allDay) {
        this.userId = userId;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    // 부분 수정용. 서비스에서 '기존값 + 전달값'을 합친 최종값으로 호출한다.
    // 영속 상태 엔티티라 필드만 바꾸면 트랜잭션 커밋 시 더티 체킹으로 UPDATE가 나간다(save 불필요).
    public void update(String title, LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

}
