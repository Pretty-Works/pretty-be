package HK.PrettyWorks_BE.project.meeting.repository;

import HK.PrettyWorks_BE.project.meeting.domain.MeetingAttendeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendeeEntity, Long> {
    List<MeetingAttendeeEntity> findByMeetingId(Long meetingId);
    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    void deleteByMeetingId(Long meetingId);
}