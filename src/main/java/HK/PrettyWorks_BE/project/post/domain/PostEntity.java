package HK.PrettyWorks_BE.project.post.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_posts")
@SQLDelete(sql = "UPDATE project_posts SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL",
        verify = Expectation.RowCount.class
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 프로젝트 ID
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // 작성자 ID
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    // 제목
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 중요도
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private PostPriority priority;

    // 내용
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 삭제 시각
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public PostEntity(Long projectId, Long authorId, String title,
                      PostPriority priority, String content) {
        this.projectId = projectId;
        this.authorId = authorId;
        this.title = title;
        this.priority = priority;
        this.content = content;
    }

    // 수정 가능한 값만 변경
    public void update(String title, PostPriority priority, String content) {
        this.title = title;
        this.priority = priority;
        this.content = content;
    }
}
