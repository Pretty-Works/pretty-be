package HK.PrettyWorks_BE.project.post.repository;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.domain.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    // 목록 조회 - 제목 부분 일치 검색 + 중요도 필터 + 최신순
    @Query(value = """
            SELECT p
            FROM PostEntity p
            WHERE p.projectId = :projectId
              AND (:title IS NULL OR p.title LIKE CONCAT('%', :title, '%00'))
              AND (:priority IS NULL OR p.priority = :priority)
            ORDER BY p.createdAt DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(p)
            FROM PostEntity p
            WHERE p.projectId = :projectId
              AND (:title IS NULL OR p.title LIKE CONCAT('%', :title, '%'))
              AND (:priority IS NULL OR p.priority = :priority)
            """)
    Page<PostEntity> findPostSummaries(
            @Param("projectId") Long projectId,
            @Param("title") String title,
            @Param("priority") PostPriority priority,
            Pageable pageable);
}
