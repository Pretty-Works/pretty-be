package HK.PrettyWorks_BE.project.meeting.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meeting_attendees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAttendeeEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 회의록 ID (meetings 테이블의 id)
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    // 사용자 ID (참석자) (users 테이블의 id)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 참석자 이름
    @Column(name = "attendee_name", nullable = false, length = 20)
    private String attendeeName;

    // 참석자 부서
    @Column(name = "attendee_department", nullable = false, length = 30)
    private String attendeeDepartment;

    // 역할
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Builder
    public MeetingAttendeeEntity(Long meetingId, Long userId, String attendeeName,
                                 String attendeeDepartment, String role) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.attendeeName = attendeeName;
        this.attendeeDepartment = attendeeDepartment;
        this.role = role;
    }
}