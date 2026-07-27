package HK.PrettyWorks_BE.calendar.leave.domain;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_leaves")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleLeaveEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 연결된 일정 FK (1:1). 이 행의 존재 자체가 "이 일정은 휴가"임을 뜻한다.
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "reason", length = 255)
    private String reason;

    // 신청 일수(연차 사용/잔여 계산용). startDate~endDate 포함 일수.
    @Column(name = "days", nullable = false)
    private int days;

    @Builder
    public ScheduleLeaveEntity(Long scheduleId, LeaveType leaveType, String reason, int days) {
        this.scheduleId = scheduleId;
        this.leaveType = leaveType;
        this.reason = reason;
        this.days = days;
    }

    // 수정용. 서비스에서 최종값으로 호출(더티 체킹으로 UPDATE).
    public void update(LeaveType leaveType, String reason, int days) {
        this.leaveType = leaveType;
        this.reason = reason;
        this.days = days;
    }
}
