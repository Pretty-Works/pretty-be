package HK.PrettyWorks_BE.project.meeting.repository;

import HK.PrettyWorks_BE.project.meeting.domain.MeetingEntity;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface MeetingRepository extends JpaRepository<MeetingEntity, Long> {
    // 문서 번호 생성
    @Query(value = "SELECT COUNT(*) FROM meetings", nativeQuery = true)
    long countAllIncludingDeleted();

    // 목록 조회 - 프로젝션
    @Query(value = """
            SELECT m
            FROM MeetingEntity m
            WHERE m.projectId = :projectId
              AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
              AND (:attendeeName IS NULL OR EXISTS (
                    SELECT 1 FROM MeetingAttendeeEntity a
                    WHERE a.meetingId = m.id AND a.attendeeName = :attendeeName
              ))
            """,
            countQuery = """
            SELECT COUNT(m)
            FROM MeetingEntity m
            WHERE m.projectId = :projectId
              AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
              AND (:attendeeName IS NULL OR EXISTS (
                    SELECT 1 FROM MeetingAttendeeEntity a
                    WHERE a.meetingId = m.id AND a.attendeeName = :attendeeName
              ))
            """)
    Page<MeetingEntity> findMeetingSummaries(
            @Param("projectId") Long projectId,
            @Param("title") String title,
            @Param("attendeeName") String attendeeName,
            Pageable pageable);
}