package HK.PrettyWorks_BE.agent.execution.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "seq", nullable = false) private long seq;
    @Column(name = "event_type", nullable = false, length = 20) private String eventType;
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT") private String payload;
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;

    public AgentEventEntity(Long runId, long seq, String eventType, String payload) {
        this.runId = runId;
        this.seq = seq;
        this.eventType = eventType;
        this.payload = payload;
    }
}
