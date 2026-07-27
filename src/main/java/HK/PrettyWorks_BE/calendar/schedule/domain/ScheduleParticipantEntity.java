package HK.PrettyWorks_BE.calendar.schedule.domain;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.converter.ParticipantRoleConverter;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleParticipantEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 코드에선 role(WRITER/PARTICIPANT)로 다루고, DB에는 is_writer BOOLEAN(1: WRITER / 0: PARTICIPANT)으로 컨버터가 매핑.
    @Convert(converter = ParticipantRoleConverter.class)
    @Column(name = "is_writer", nullable = false)
    private ParticipantRole role;

    // 나간 시각(soft delete). NULL이면 활성 참가자. 나가기 시 세팅된다.
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Builder
    public ScheduleParticipantEntity(Long scheduleId, Long userId, ParticipantRole role) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.role = role;
    }

    // 활성 참가자 여부. left_at이 NULL이면 활성(안 나감).
    public boolean isActive() {
        return leftAt == null;
    }

    // 나가기(soft delete). 영속 상태라 커밋 시 더티 체킹으로 UPDATE가 나간다.
    public void leave() {
        this.leftAt = LocalDateTime.now();
    }

}
