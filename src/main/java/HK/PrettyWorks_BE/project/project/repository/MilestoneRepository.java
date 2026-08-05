package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {

    // 수정 API: 프로젝트의 마일스톤 전체 (요청과 대조해 신규/갱신/삭제를 가른다).
    List<MilestoneEntity> findByProjectId(Long projectId);

    // 상세·목록 조회: 목표일 오름차순. (project_id, target_date) 복합 인덱스를 그대로 탄다.
    // 같은 목표일이 흔해 id 보조 정렬을 둔다 — 없으면 조회할 때마다 순서가 바뀔 수 있고,
    // '목표 마일스톤'(미완료 중 첫 항목)이 요청마다 달라진다. id는 AUTO_INCREMENT라 등록 순서와 같다.
    // 완료 토글도 이 조회를 쓴다. 순서 규칙을 판정하려면 대상 한 건이 아니라 앞뒤 항목이 필요하고,
    // 프로젝트 소속 여부는 목록에 그 id가 있는지로 함께 확인된다.
    List<MilestoneEntity> findByProjectIdOrderByTargetDateAscIdAsc(Long projectId);
}
