package HK.PrettyWorks_BE.agent.conversation.persistence;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageAttachmentEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentMessageAttachmentRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AgentMessageAttachmentRepository
        extends JpaRepository<AgentMessageAttachmentEntity, Long> {

    // 스레드 상세: 화면에 실린 말풍선들의 첨부를 한 번에 모은다(말풍선마다 조회하면 N+1).
    // 정렬은 (메시지, 사용자가 고른 순서) — 서비스에서 messageId로 묶기만 하면 된다.
    @Query("select new HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentMessageAttachmentRow(" +
            "a.messageId, a.filename, a.contentType, a.sizeBytes) " +
            "from AgentMessageAttachmentEntity a " +
            "where a.messageId in :messageIds " +
            "order by a.messageId, a.seq")
    List<AgentMessageAttachmentRow> findRowsByMessageIdIn(
            @Param("messageIds") Collection<Long> messageIds);
}
