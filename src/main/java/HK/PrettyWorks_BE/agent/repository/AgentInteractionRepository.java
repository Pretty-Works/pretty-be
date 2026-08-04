package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.constant.AgentDecision;
import HK.PrettyWorks_BE.agent.constant.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentInteractionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface AgentInteractionRepository extends JpaRepository<AgentInteractionEntity, Long> {
    Optional<AgentInteractionEntity> findByRunIdAndToolCallId(Long runId, String toolCallId);
    Optional<AgentInteractionEntity> findByTokenHash(String tokenHash);
    List<AgentInteractionEntity> findByRunIdAndStatusOrderById(
            Long runId, AgentInteractionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AgentInteractionEntity> findByRunIdOrderById(Long runId);

    @Query("select new HK.PrettyWorks_BE.agent.repository.AgentPendingInteractionRow("
            + "i.id, i.kind, i.label, i.payloadJson, r.runId, r.conversationId, c.title, "
            + "i.expiresAt, i.createdAt) "
            + "from AgentInteractionEntity i, AgentRunEntity r, AgentConversationEntity c "
            + "where i.runId = r.id and r.conversationId = c.id "
            + "and r.userId = :userId and i.status = :interactionStatus "
            + "and r.status in :runStatuses and i.expiresAt > :now "
            + "order by i.createdAt desc")
    List<AgentPendingInteractionRow> findPendingRows(
            @Param("userId") Long userId,
            @Param("interactionStatus") AgentInteractionStatus interactionStatus,
            @Param("runStatuses") Collection<AgentRunStatus> runStatuses,
            @Param("now") LocalDateTime now);

    @Query("select i.id from AgentInteractionEntity i "
            + "where i.status = :status and i.expiresAt <= :now order by i.id")
    List<Long> findExpiredIds(@Param("status") AgentInteractionStatus status,
                              @Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AgentInteractionEntity i where i.id = :id")
    Optional<AgentInteractionEntity> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AgentInteractionEntity i set i.status = :next, i.decision = :decision,
                   i.alternativeId = :alternativeId, i.responseJson = :responseJson,
                   i.reason = :reason, i.resolvedAt = :now, i.modifiedAt = :now
             where i.id = :id and i.status = :pending
            """)
    int resolvePending(@Param("id") Long id,
                       @Param("pending") AgentInteractionStatus pending,
                       @Param("next") AgentInteractionStatus next,
                       @Param("decision") AgentDecision decision,
                       @Param("alternativeId") String alternativeId,
                       @Param("responseJson") String responseJson,
                       @Param("reason") String reason,
                       @Param("now") LocalDateTime now);
}
