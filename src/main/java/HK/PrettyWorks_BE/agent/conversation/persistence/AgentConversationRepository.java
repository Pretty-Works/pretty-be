package HK.PrettyWorks_BE.agent.conversation.persistence;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {

    // 대화 내역(햄버거): 내 스레드를 최신순으로. 커서가 null이면 첫 페이지, 있으면 그보다 오래된 것부터.
    // 패널의 "최근 대화" 3건과 "전체보기"가 Pageable 크기만 바꿔 같은 쿼리를 쓴다.
    //
    // 정렬 키가 last_message_at 하나로는 부족해 id를 덧붙인다. 같은 마이크로초에 두 대화가 걸리면
    // 순서가 흔들려 경계에서 한 건이 중복되거나 빠진다. 이 tie-break 때문에 조건이 OR로 갈라진다.
    // idx_agent_conversations_user_recent (user_id, deleted_at, last_message_at) 를 그대로 타며,
    // InnoDB 보조 인덱스는 PK를 뒤에 달고 있어 id 정렬까지 인덱스 순서로 해결된다.
    //
    // 지워진 대화를 빼는 조건은 여기 없다 — 엔티티의 @SQLRestriction 이 붙여 준다.
    // 인덱스에서 deleted_at 이 user_id 바로 뒤에 있는 것도 그래서다(둘 다 동등 조건).
    @Query("""
            select c from AgentConversationEntity c
            where c.userId = :userId
              and (:cursorLastMessageAt is null
                   or c.lastMessageAt < :cursorLastMessageAt
                   or (c.lastMessageAt = :cursorLastMessageAt and c.id < :cursorId))
            order by c.lastMessageAt desc, c.id desc
            """)
    List<AgentConversationEntity> findScroll(@Param("userId") Long userId,
                                             @Param("cursorLastMessageAt") LocalDateTime cursorLastMessageAt,
                                             @Param("cursorId") Long cursorId,
                                             Pageable pageable);

    // 삭제도 이 잠금을 쓴다. 새 실행을 시작하는 쪽(AgentRunFactory)이 같은 행을 PESSIMISTIC_WRITE로
    // 잡고 나서 진행 중 실행을 확인하므로, 삭제가 먼저 잡고 있어야 "진행 중 없음"을 확인한 직후에
    // 실행이 끼어드는 창이 닫힌다.
    //
    // 이미 지워진 대화는 @SQLRestriction 때문에 여기 잡히지 않아 호출부에서 404가 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversationEntity c where c.id = :id")
    Optional<AgentConversationEntity> findByIdForUpdate(@Param("id") Long id);
}
