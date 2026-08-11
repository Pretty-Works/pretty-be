package HK.PrettyWorks_BE.agent.execution.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunEntity extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "run_id", nullable = false, length = 40, unique = true) private String runId;
    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "session_id", length = 64) private String sessionId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    private AgentRunStatus status;
    @Column(name = "goal", nullable = false, columnDefinition = "TEXT") private String goal;
    @Column(name = "screen_context", columnDefinition = "TEXT") private String screenContext;
    @Column(name = "last_event_seq", nullable = false) private long lastEventSeq;
    @Column(name = "question_count", nullable = false) private int questionCount;
    @Column(name = "tool_call_count", nullable = false) private int toolCallCount;
    @Column(name = "error_code", length = 20) private String errorCode;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "last_active_at", nullable = false) private LocalDateTime lastActiveAt;
    @Column(name = "finished_at") private LocalDateTime finishedAt;

    @Builder
    public AgentRunEntity(String runId, Long conversationId, Long userId, String sessionId,
                          String goal, String screenContext, LocalDateTime startedAt) {
        this.runId = runId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.status = AgentRunStatus.RUNNING;
        this.goal = goal;
        this.screenContext = screenContext;
        this.startedAt = startedAt;
        this.lastActiveAt = startedAt;
    }

    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }

    public long issueNextEventSequence(LocalDateTime now) {
        this.lastEventSeq++;
        this.lastActiveAt = now;
        return this.lastEventSeq;
    }

    public void registerToolCall(LocalDateTime now) {
        this.toolCallCount++;
        this.lastActiveAt = now;
    }

    public void registerQuestion(LocalDateTime now) {
        this.questionCount++;
        this.lastActiveAt = now;
    }

    public void transition(AgentRunStatus next, String errorCode, LocalDateTime now) {
        this.status = next;
        this.errorCode = errorCode;
        this.lastActiveAt = now;
        this.finishedAt = next.isTerminal() ? now : null;
    }
}
