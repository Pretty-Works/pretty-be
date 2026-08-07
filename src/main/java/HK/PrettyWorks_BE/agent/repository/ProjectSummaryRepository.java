package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.domain.ProjectSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSummaryRepository extends JpaRepository<ProjectSummaryEntity, Long> {

    // 프로젝트당 행이 섹션 수(4)뿐이라 section 필터는 서비스에서 건다.
    // 조회 조건을 나누면 쿼리가 둘이 되는데, 걸러낼 데이터가 세 행이라 얻는 게 없다.
    List<ProjectSummaryEntity> findByProjectIdOrderByDisplayOrderAscIdAsc(Long projectId);
}
