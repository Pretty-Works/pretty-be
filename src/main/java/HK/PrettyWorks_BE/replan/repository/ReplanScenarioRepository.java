package HK.PrettyWorks_BE.replan.repository;

import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.domain.ReplanScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReplanScenarioRepository extends JpaRepository<ReplanScenarioEntity, Long> {

    // 적용 시 대상 시나리오 조회. (replan_id, scenario_type) UNIQUE 이므로 한 건만 나온다.
    Optional<ReplanScenarioEntity> findByReplanIdAndScenarioType(Long replanId, ReplanScenarioType scenarioType);

    // 저장 직후 응답과 승인 미리보기에서 사용. 저장 순서를 유지한다.
    List<ReplanScenarioEntity> findByReplanIdOrderByIdAsc(Long replanId);
}
