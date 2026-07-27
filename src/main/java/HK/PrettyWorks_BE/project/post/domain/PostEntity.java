package HK.PrettyWorks_BE.project.post.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_posts")
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

    // 내용
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 삭제 시각
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public PostEntity(Long projectId, Long authorId, String title,
                      String content, LocalDateTime deletedAt) {
        this.projectId = projectId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
    }

    // 수정 관련 내용 작성 예정

}