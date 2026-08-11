package HK.PrettyWorks_BE.project.meeting.repository;

import HK.PrettyWorks_BE.project.meeting.domain.MeetingEntity;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface MeetingRepository extends JpaRepository<MeetingEntity, Long> {
    // 문서 번호 생성
    @Query(value = "SELECT COUNT(*) FROM meetings", nativeQuery = true)
    long countAllIncludingDeleted();

    // 목록 조회 - 프로젝션
    // from·to는 회의 일자 구간 필터다. 화면은 둘 다 null로 부르고, 기간으로 묻는 에이전트 도구만 채운다.
    @Query(value = """
            SELECT m
            FROM MeetingEntity m
            WHERE m.projectId = :projectId
              AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
              AND (:from IS NULL OR m.meetingDate >= :from)
              AND (:to IS NULL OR m.meetingDate <= :to)
              AND (:attendeeName IS NULL OR EXISTS (
                    SELECT 1 FROM MeetingAttendeeEntity a
                    WHERE a.meetingId = m.id AND a.attendeeName = :attendeeName
              ))
            ORDER BY m.meetingDate DESC, m.id DESC
            """,
            countQuery = """
            SELECT COUNT(m)
            FROM MeetingEntity m
            WHERE m.projectId = :projectId
              AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
              AND (:from IS NULL OR m.meetingDate >= :from)
              AND (:to IS NULL OR m.meetingDate <= :to)
              AND (:attendeeName IS NULL OR EXISTS (
                    SELECT 1 FROM MeetingAttendeeEntity a
                    WHERE a.meetingId = m.id AND a.attendeeName = :attendeeName
              ))
            """)
    Page<MeetingEntity> findMeetingSummaries(
            @Param("projectId") Long projectId,
            @Param("title") String title,
            @Param("attendeeName") String attendeeName,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    // 후속 조치만 일괄 조회. 프로젝트 AI 요약이 "후속 액션 미정리" 건수를 세는 데 쓴다.
    // MeetingEntity의 @SQLRestriction("deleted_at IS NULL")이 적용되어 삭제된 회의록은 제외된다.
    @Query("""
            SELECT new HK.PrettyWorks_BE.project.meeting.repository.MeetingFollowUpRow(m.id, m.followUp)
            FROM MeetingEntity m
            WHERE m.id IN :meetingIds
            """)
    List<MeetingFollowUpRow> findFollowUps(@Param("meetingIds") List<Long> meetingIds);

    // 프로젝트 기간 축소 검증: 새 기간을 벗어나는 회의 일자가 남는지 확인한다.
    // MeetingEntity의 @SQLRestriction("deleted_at IS NULL")이 적용되어 소프트 삭제된 회의록은 제외된다.
    @Query("""
            SELECT COUNT(m) > 0
            FROM MeetingEntity m
            WHERE m.projectId = :projectId
              AND (m.meetingDate < :startDate OR m.meetingDate > :endDate)
            """)
    boolean existsOutsidePeriod(@Param("projectId") Long projectId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}
