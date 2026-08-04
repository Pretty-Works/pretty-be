package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.domain.AgentEventEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentEventRepository extends JpaRepository<AgentEventEntity, Long> {
    Optional<AgentEventEntity> findByRunIdAndSeq(Long runId, long seq);
    List<AgentEventEntity> findByRunIdAndSeqGreaterThanOrderBySeq(Long runId, long seq);
    List<AgentEventEntity> findByRunIdAndSeqGreaterThanAndSeqLessThanEqualOrderBySeq(
            Long runId, long afterSeq, long throughSeq);
    long countByRunIdAndEventType(Long runId, String eventType);

    @Modifying
    @Query("delete from AgentEventEntity e where e.runId in :runIds")
    int deleteByRunIds(@Param("runIds") Collection<Long> runIds);
}
