package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {

    // 수정 API: 프로젝트의 마일스톤 전체 (현재 목록과 비교 후 다르면 교체).
    List<MilestoneEntity> findByProjectId(Long projectId);

    // 상세 조회 API: 목표일 오름차순. (project_id, target_date) 복합 인덱스를 그대로 탄다.
    List<MilestoneEntity> findByProjectIdOrderByTargetDateAsc(Long projectId);
}
