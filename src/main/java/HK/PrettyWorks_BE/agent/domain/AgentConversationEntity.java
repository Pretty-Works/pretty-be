package HK.PrettyWorks_BE.agent.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentConversationEntity extends BaseTimeEntity {

    // 제목 컬럼 길이. LLM이 더 긴 제목을 내려줄 수 있어 저장 전에 이 길이로 자른다.
    private static final int TITLE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 대화 소유자. FK는 raw Long 컬럼으로 유지(기존 컨벤션).
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 스레드 제목. 사용자가 붙이지 않는다 — LLM이 첫 응답에서 요약해 주면 그 값을 쓰고,
    // 없거나 호출이 실패했으면 첫 질문 앞부분으로 폴백한다.
    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    // 대화 내역 목록의 정렬 기준.
    // 메시지 추가는 agent_messages INSERT라 스레드 행 자체는 변하지 않아 modifiedAt이 갱신되지
    // 않는다. 그래서 정렬 컬럼을 따로 두고 명시적으로 관리한다.
    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    // 대화 단위 자동 승인. 기본은 반드시 false이며, JWT 인증 외부 API에서만 바꾼다.
    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove;

    @Builder
    public AgentConversationEntity(Long userId, String title, LocalDateTime lastMessageAt, Boolean autoApprove) {
        this.userId = userId;
        this.title = truncate(title);
        this.lastMessageAt = lastMessageAt;
        this.autoApprove = Boolean.TRUE.equals(autoApprove);
    }

    // 메시지를 추가할 때마다 호출해 목록에서 이 스레드를 맨 위로 끌어올린다.
    public void touch(LocalDateTime now) {
        this.lastMessageAt = now;
    }

    // LLM이 제목을 내려줬을 때만 교체한다. 비어 있으면 폴백으로 저장해 둔 제목을 그대로 둔다.
    public void renameTitle(String title) {
        if (title == null || title.isBlank()) {
            return;
        }

        this.title = truncate(title);
    }

    // 소유권 검사. conversationId만 바꿔 남의 대화에 붙는 것을 막는다. (Security는 이걸 모른다)
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void changeAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    private static String truncate(String value) {
        String trimmed = value.trim();

        return trimmed.length() <= TITLE_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

}
