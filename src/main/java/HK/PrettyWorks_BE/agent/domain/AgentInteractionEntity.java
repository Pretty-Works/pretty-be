package HK.PrettyWorks_BE.agent.domain;

import HK.PrettyWorks_BE.agent.constant.*;
import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_interactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentInteractionEntity extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Enumerated(EnumType.STRING) @Column(name = "kind", nullable = false, length = 20)
    private AgentInteractionKind kind;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    private AgentInteractionStatus status;
    @Column(name = "label", nullable = false, length = 60) private String label;
    @Column(name = "payload_json", columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(name = "tool_call_id", length = 64) private String toolCallId;
    @Column(name = "tool", length = 50) private String tool;
    @Enumerated(EnumType.STRING) @Column(name = "access", length = 10) private AgentAccessType access;
    @Column(name = "preview_text", columnDefinition = "LONGTEXT") private String previewText;
    @Column(name = "params_canonical", columnDefinition = "LONGTEXT") private String paramsCanonical;
    @Column(name = "params_hash", columnDefinition = "CHAR(64)") private String paramsHash;
    @Column(name = "auto_approved", nullable = false) private boolean autoApproved;
    @Column(name = "token_hash", columnDefinition = "CHAR(64)", unique = true) private String tokenHash;
    @Column(name = "token_expires_at") private LocalDateTime tokenExpiresAt;
    @Column(name = "token_used_at") private LocalDateTime tokenUsedAt;
    @Column(name = "token_revoked_at") private LocalDateTime tokenRevokedAt;
    @Column(name = "executed_at") private LocalDateTime executedAt;
    @Column(name = "result_json", columnDefinition = "LONGTEXT") private String resultJson;
    @Column(name = "question_text", length = 200) private String questionText;
    @Enumerated(EnumType.STRING) @Column(name = "decision", length = 20) private AgentDecision decision;
    @Column(name = "alternative_id", length = 40) private String alternativeId;
    @Column(name = "response_json", columnDefinition = "LONGTEXT") private String responseJson;
    @Column(name = "reason", length = 200) private String reason;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    @Builder
    public AgentInteractionEntity(Long runId, AgentInteractionKind kind, String label, String payloadJson,
                                  String toolCallId, String tool, AgentAccessType access, String previewText,
                                  String paramsCanonical, String paramsHash, boolean autoApproved,
                                  String questionText, LocalDateTime expiresAt, LocalDateTime resolvedAt) {
        this.runId = runId;
        this.kind = kind;
        this.status = autoApproved ? AgentInteractionStatus.APPROVED : AgentInteractionStatus.PENDING;
        this.label = label;
        this.payloadJson = payloadJson;
        this.toolCallId = toolCallId;
        this.tool = tool;
        this.access = access;
        this.previewText = previewText;
        this.paramsCanonical = paramsCanonical;
        this.paramsHash = paramsHash;
        this.autoApproved = autoApproved;
        this.questionText = questionText;
        this.expiresAt = expiresAt;
        this.resolvedAt = autoApproved ? resolvedAt : null;
    }

    public boolean isWriteApproval() {
        return kind == AgentInteractionKind.APPROVAL && access == AgentAccessType.WRITE;
    }

    public boolean tokenExpired(LocalDateTime now) {
        return tokenExpiresAt == null || !tokenExpiresAt.isAfter(now);
    }

    public void issueToken(String tokenHash, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.tokenExpiresAt = expiresAt;
        this.tokenUsedAt = null;
        this.tokenRevokedAt = null;
    }

    public void completeExecution(String resultJson, LocalDateTime now) {
        this.tokenUsedAt = now;
        this.executedAt = now;
        this.resultJson = resultJson;
    }

    public void revokeToken(LocalDateTime now) {
        if (executedAt == null) {
            this.tokenRevokedAt = now;
        }
    }

    public void replaceDisplayPayload(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public boolean expirePending(LocalDateTime now) {
        if (status != AgentInteractionStatus.PENDING) {
            return false;
        }
        this.status = AgentInteractionStatus.EXPIRED;
        this.resolvedAt = now;
        return true;
    }
}
