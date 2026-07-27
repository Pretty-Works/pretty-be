package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    // 수정 API용 조회. 커밋 시 프로젝트 version을 무조건 1 올리고 검증한다.
    // 멤버·마일스톤만 바뀌면 projects 행 자체는 그대로라 version이 오르지 않아 동시 수정을 놓치는데,
    // FORCE_INCREMENT로 애그리거트(프로젝트+멤버+마일스톤) 단위 충돌을 잡는다.
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from ProjectEntity p where p.id = :id")
    Optional<ProjectEntity> findByIdWithOptimisticLock(@Param("id") Long id);
}
