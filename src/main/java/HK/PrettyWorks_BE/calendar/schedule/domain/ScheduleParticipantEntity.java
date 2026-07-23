package HK.PrettyWorks_BE.calendar.schedule.domain;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import HK.PrettyWorks_BE.calendar.schedule.converter.ParticipantRoleConverter;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Builder
    public ScheduleParticipantEntity(Long scheduleId, Long userId, ParticipantRole role) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.role = role;
    }

}
