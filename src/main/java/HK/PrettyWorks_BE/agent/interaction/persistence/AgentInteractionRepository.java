package HK.PrettyWorks_BE.agent.interaction.persistence;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingInteractionRow;
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

    @Query("select new HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingInteractionRow("
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

    // 대화 상세: 그 대화에서 오간 승인 카드 전부(대기·완료·만료 모두). 오래된 순으로 그린다.
    @Query("select new HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentApprovalRow("
            + "i.id, i.runId, i.access, i.label, i.previewText, i.payloadJson, "
            + "i.status, i.alternativeId, i.resolvedAt) "
            + "from AgentInteractionEntity i, AgentRunEntity r "
            + "where i.runId = r.id and r.conversationId = :conversationId and i.kind = :kind "
            + "order by i.id")
    List<AgentApprovalRow> findApprovalRows(@Param("conversationId") Long conversationId,
                                            @Param("kind") AgentInteractionKind kind);

    // 대화 목록의 '확인 필요' 배지. 실행 단위로 대기 중인 승인 카드만 모아 온다.
    @Query("select new HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingApprovalRow(i.runId, i.id) "
            + "from AgentInteractionEntity i "
            + "where i.runId in :runIds and i.kind = :kind and i.status = :status "
            + "order by i.id desc")
    List<AgentPendingApprovalRow> findPendingApprovalRows(
            @Param("runIds") Collection<Long> runIds,
            @Param("kind") AgentInteractionKind kind,
            @Param("status") AgentInteractionStatus status);

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
