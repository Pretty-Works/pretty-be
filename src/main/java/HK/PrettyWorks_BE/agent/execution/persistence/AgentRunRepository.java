package HK.PrettyWorks_BE.agent.execution.persistence;

import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentConversationRunRow;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, Long> {
    Optional<AgentRunEntity> findByRunId(String runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentRunEntity r where r.id = :id")
    Optional<AgentRunEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByConversationIdAndStatusIn(Long conversationId, Collection<AgentRunStatus> statuses);
    long countByUserIdAndStatusIn(Long userId, Collection<AgentRunStatus> statuses);

    // 서버 전체의 동시 실행 수. 한 사람이 한도를 안 넘어도 여럿이 겹치면 FastAPI·LLM 쪽이 먼저 무너진다.
    long countByStatusIn(Collection<AgentRunStatus> statuses);
    List<AgentRunEntity> findByConversationIdAndStatusIn(
            Long conversationId, Collection<AgentRunStatus> statuses);

    // 대화 목록 화면용. 대화마다 가장 최근 실행 하나만 쓰므로 id 내림차순으로 받아 앞의 행만 남긴다.
    @Query("select new HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentConversationRunRow("
            + "r.conversationId, r.id, r.runId, r.status) "
            + "from AgentRunEntity r where r.conversationId in :conversationIds "
            + "order by r.conversationId, r.id desc")
    List<AgentConversationRunRow> findRunRowsByConversationIdIn(
            @Param("conversationIds") Collection<Long> conversationIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRunEntity r set r.status = :next, r.errorCode = :errorCode,
                   r.lastActiveAt = :now, r.finishedAt = :finishedAt, r.modifiedAt = :now
             where r.id = :id and r.status in :expected
            """)
    int transition(@Param("id") Long id, @Param("expected") Collection<AgentRunStatus> expected,
                   @Param("next") AgentRunStatus next, @Param("errorCode") String errorCode,
                   @Param("now") LocalDateTime now, @Param("finishedAt") LocalDateTime finishedAt);

    @Query("select distinct r.id from AgentRunEntity r, AgentEventEntity e "
            + "where e.runId = r.id and r.status in :statuses and r.finishedAt < :threshold "
            + "order by r.id")
    List<Long> findTerminalIdsFinishedBefore(@Param("statuses") Collection<AgentRunStatus> statuses,
                                             @Param("threshold") LocalDateTime threshold,
                                             Pageable pageable);

    @Query("select r.id from AgentRunEntity r "
            + "where r.status = :status and r.startedAt <= :threshold order by r.id")
    List<Long> findTimedOutIds(@Param("status") AgentRunStatus status,
                               @Param("threshold") LocalDateTime threshold, Pageable pageable);
}
