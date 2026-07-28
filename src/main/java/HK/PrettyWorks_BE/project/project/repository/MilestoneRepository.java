package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {

    // 수정 API: 프로젝트의 마일스톤 전체 (요청과 대조해 신규/갱신/삭제를 가른다).
    List<MilestoneEntity> findByProjectId(Long projectId);

    // 상세·목록 조회: 목표일 오름차순. (project_id, target_date) 복합 인덱스를 그대로 탄다.
    // 같은 목표일이 흔해 id 보조 정렬을 둔다 — 없으면 조회할 때마다 순서가 바뀔 수 있고,
    // '목표 마일스톤'(미완료 중 첫 항목)이 요청마다 달라진다. id는 AUTO_INCREMENT라 등록 순서와 같다.
    List<MilestoneEntity> findByProjectIdOrderByTargetDateAscIdAsc(Long projectId);

    // 완료 토글: 마일스톤 존재와 프로젝트 소속을 한 번에 확인한다.
    // id는 테이블 전역으로 부여되어 다른 프로젝트의 id도 유효한 값이므로, projectId를 함께 걸어야 한다.
    Optional<MilestoneEntity> findByIdAndProjectId(Long id, Long projectId);
}
