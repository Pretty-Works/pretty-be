package HK.PrettyWorks_BE.project.post.repository;

import HK.PrettyWorks_BE.project.post.domain.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

}