package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, Long> {

    // 스레드 상세: 이 대화의 말풍선을 오래된 순으로. id 오름차순이 곧 시간순이다.
    // rawJson을 빼려고 엔티티가 아닌 프로젝션으로 받는다.
    @Query("select new HK.PrettyWorks_BE.agent.repository.AgentMessageRow(" +
            "m.id, m.runId, m.role, m.content, m.success, m.actionType, m.actionJson, m.createdAt) " +
            "from AgentMessageEntity m " +
            "where m.conversationId = :conversationId " +
            "order by m.id")
    List<AgentMessageRow> findMessageRows(@Param("conversationId") Long conversationId);

    // FastAPI에 넘길 대화 맥락: 최근 N건을 최신순으로 뽑는다. (서비스에서 뒤집어 오래된 순으로 보낸다)
    // success가 false인 AGENT 메시지는 제외한다 — "연결하지 못했습니다" 같은 실패 안내가
    // 대화 맥락으로 전달되면 안 된다. USER 행은 success가 null이라 자연히 포함된다.
    @Query("select new HK.PrettyWorks_BE.agent.repository.AgentContextRow(m.role, m.content) " +
            "from AgentMessageEntity m " +
            "where m.conversationId = :conversationId " +
            "and (m.success is null or m.success = true) " +
            "order by m.id desc")
    List<AgentContextRow> findRecentContext(@Param("conversationId") Long conversationId, Pageable pageable);

    // 대화 목록의 "안 읽음" 판정용. 페이지에 실린 대화들의 마지막 AGENT 메시지 id를 한 번에 모은다
    // (행마다 조회하면 N+1). idx_agent_messages_conversation (conversation_id, id)를 탄다.
    @Query("select new HK.PrettyWorks_BE.agent.repository.AgentLastAgentMessageRow(" +
            "m.conversationId, max(m.id)) " +
            "from AgentMessageEntity m " +
            "where m.conversationId in :conversationIds and m.role = :role " +
            "group by m.conversationId")
    List<AgentLastAgentMessageRow> findLastAgentMessageRows(
            @Param("conversationIds") Collection<Long> conversationIds,
            @Param("role") AgentRole role);

    // 읽음 처리 지점. 역할을 가리지 않고 최신 id를 쓴다 — 지금 화면에 보이는 마지막 말풍선이
    // USER 행일 수도 있고, 그 뒤에 오는 AGENT 답변은 어차피 더 큰 id라 안 읽음으로 남는다.
    @Query("select max(m.id) from AgentMessageEntity m where m.conversationId = :conversationId")
    Long findLatestId(@Param("conversationId") Long conversationId);

    // Run 시작 메시지는 이미 저장된 뒤 FastAPI 요청을 조립하므로, 방금 USER 메시지는 제외한다.
    @Query("select new HK.PrettyWorks_BE.agent.repository.AgentContextRow(m.role, m.content) " +
            "from AgentMessageEntity m " +
            "where m.conversationId = :conversationId and m.id < :beforeMessageId " +
            "and (m.success is null or m.success = true) " +
            "order by m.id desc")
    List<AgentContextRow> findRecentContextBeforeMessage(
            @Param("conversationId") Long conversationId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable);

}
