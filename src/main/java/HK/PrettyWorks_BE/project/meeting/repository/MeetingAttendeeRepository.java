package HK.PrettyWorks_BE.project.meeting.repository;

import HK.PrettyWorks_BE.project.meeting.constant.MeetingRole;
import HK.PrettyWorks_BE.project.meeting.domain.MeetingAttendeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendeeEntity, Long> {
    // 회의록 1건의 참석자 조회 (상세)
    List<MeetingAttendeeEntity> findByMeetingId(Long meetingId);

    // 여러 회의록의 참석자 한 번에 조회 (목록 N+1 방지)
    List<MeetingAttendeeEntity> findByMeetingIdIn(List<Long> meetingIds);

    // 특정 사용자가 이 회의록 참석자인지 (수정 권한 판정용)
    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    // 역할별 참석자 조회. 수정 시 명단 diff에서 ATTENDEE 행만 대상으로 삼는다 (WRITER 행은 건드리지 않는다).
    List<MeetingAttendeeEntity> findByMeetingIdAndRole(Long meetingId, MeetingRole role);
}
