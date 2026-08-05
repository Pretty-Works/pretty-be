package HK.PrettyWorks_BE.project.meeting.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meetings")
@SQLDelete(
        sql = "UPDATE meetings SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL",
        verify = Expectation.RowCount.class
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 프로젝트 ID (projects 테이블의 id)
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // 사용자 ID (작성자) (users 테이블의 id)
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    // 문서 번호
    @Column(name = "document_no", nullable = false, length = 30, unique = true)
    private String documentNo;

    // 회의명
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 회의 일자
    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    // 장소
    @Column(name = "location", length = 100)
    private String location;

    // 회의 목적
    @Column(name = "purpose", length = 500)
    private String purpose;

    // 주요 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 후속 조치
    @Column(name = "follow_up", columnDefinition = "TEXT")
    private String followUp;

    // 녹취 파일명
    @Column(name = "recording", length = 500)
    private String recording;

    // 삭제 시각
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public MeetingEntity(Long projectId, Long authorId, String documentNo, String title,
                         LocalDate meetingDate, String location, String purpose,
                         String content, String followUp, String recording) {
        this.projectId = projectId;
        this.authorId = authorId;
        this.documentNo = documentNo;
        this.title = title;
        this.meetingDate = meetingDate;
        this.location = location;
        this.purpose = purpose;
        this.content = content;
        this.followUp = followUp;
        this.recording = recording;
    }

    // 수정 가능한 값만 변경 (id·문서번호·작성자·프로젝트 제외)
    public void update(String title, LocalDate meetingDate, String location,
                       String purpose, String content, String followUp, String recording) {
        this.title = title;
        this.meetingDate = meetingDate;
        this.location = location;
        this.purpose = purpose;
        this.content = content;
        this.followUp = followUp;
        this.recording = recording;
    }

    // 저장 후 발급받은 id로 최종 문서번호를 세팅
    public void assignDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

}
