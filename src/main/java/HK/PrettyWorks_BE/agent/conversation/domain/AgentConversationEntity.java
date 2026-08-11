package HK.PrettyWorks_BE.agent.conversation.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDateTime;

// 삭제는 소프트 삭제다(project_posts·meetings와 같은 방식).
//
// 하드 삭제를 쓰지 않는 이유는 자식 행 때문이다. agent_messages·agent_runs 가 ON DELETE CASCADE로
// 매달려 있어서 대화를 지우면 실행 이력과 이벤트까지 한꺼번에 사라지는데, 그건 되돌릴 수 없고
// 실수로 지웠을 때 복구할 방법도 없다. deleted_at 만 채우면 화면에서만 사라진다.
//
// 조회 제외는 @SQLRestriction 이 전담한다. 쿼리마다 "and deletedAt is null"을 적는 방식(expenses)과
// 달리 새 쿼리를 추가하다 빠뜨릴 수가 없고, 여기 걸어두면 findById·findScroll 은 물론
// 다른 엔티티에서 조인해 들어오는 경로(대기 중 승인 카드 조회)까지 자동으로 걸러진다.
@Entity
@Table(name = "agent_conversations")
@SQLDelete(sql = "UPDATE agent_conversations SET deleted_at = NOW(6) WHERE id = ? AND deleted_at IS NULL",
        verify = Expectation.RowCount.class
)
@SQLRestriction("deleted_at IS NULL")
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

    // 사용자가 마지막으로 읽은 메시지 id. 목록의 "안 읽음" 표시가 이 값과 최신 AGENT 메시지 id를 비교한다.
    // 시각이 아니라 id인 이유는 notifications의 lastSeenNotificationId와 같다 — 같은 초에 여러 건이
    // 들어와도 경계가 흔들리지 않는다. 실행 이력을 지워도 대화는 남아야 하므로 물리 FK는 두지 않는다.
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    // 소프트 삭제 시각. 채워지면 @SQLRestriction 때문에 이 행은 어떤 조회에도 잡히지 않는다.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    // 대화를 열었을 때 읽음 지점을 옮긴다. 뒤로는 가지 않는다 —
    // 늦게 도착한 읽음 요청이 이미 읽은 답변을 다시 "안 읽음"으로 되돌리면 안 된다.
    public void markRead(Long lastMessageId) {
        if (lastMessageId == null
                || (this.lastReadMessageId != null && this.lastReadMessageId >= lastMessageId)) {
            return;
        }

        this.lastReadMessageId = lastMessageId;
    }

    // 안 읽음 여부. 판정 기준은 마지막 AGENT 메시지다 — 내가 방금 보낸 질문이 내 대화를
    // 안 읽음으로 만들면 안 된다. 답변이 아직 없는 대화(null)는 항상 읽은 상태다.
    public boolean isUnread(Long lastAgentMessageId) {
        return lastAgentMessageId != null
                && (this.lastReadMessageId == null || this.lastReadMessageId < lastAgentMessageId);
    }

    private static String truncate(String value) {
        String trimmed = value.trim();

        return trimmed.length() <= TITLE_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

}
