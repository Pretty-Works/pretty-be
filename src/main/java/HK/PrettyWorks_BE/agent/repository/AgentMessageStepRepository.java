package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.domain.AgentMessageStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AgentMessageStepRepository extends JpaRepository<AgentMessageStepEntity, Long> {

    // 종료 시 전체 step을 JVM으로 읽지 않고 DB 내부에서 영구 표시용 행으로 복사한다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into agent_message_steps (message_id, seq, payload, created_at)
            select :messageId, e.seq, e.payload, e.created_at
              from agent_events e
             where e.run_id = :runId and e.event_type = 'step'
            """, nativeQuery = true)
    int copyFromRun(@Param("messageId") Long messageId, @Param("runId") Long runId);

    @Query("""
            select new HK.PrettyWorks_BE.agent.repository.AgentMessageStepRow(
                    s.messageId, s.seq, s.payload)
              from AgentMessageStepEntity s
             where s.messageId in :messageIds
             order by s.messageId, s.seq
            """)
    List<AgentMessageStepRow> findRowsByMessageIdIn(
            @Param("messageIds") Collection<Long> messageIds);
}
