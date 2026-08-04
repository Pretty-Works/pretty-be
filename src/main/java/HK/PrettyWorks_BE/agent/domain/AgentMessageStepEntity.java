package HK.PrettyWorks_BE.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 화면에 계속 보여 줄 step 한 건. SSE 재생 원본인 agent_events와 수명이 다르므로 별도 보관한다.
@Entity
@Table(name = "agent_message_steps")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentMessageStepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 연관 엔티티를 물리지 않고 raw FK를 두는 기존 에이전트 도메인 관례를 따른다.
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "seq", nullable = false)
    private long seq;

    // step 하나는 수신 시 4KB로 제한되므로 LONGTEXT가 아니라 TEXT면 충분하다.
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AgentMessageStepEntity(Long messageId, long seq, String payload) {
        this.messageId = messageId;
        this.seq = seq;
        this.payload = payload;
    }
}
