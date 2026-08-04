package HK.PrettyWorks_BE.agent.repository;

import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {

    // 대화 내역(햄버거): 내 스레드를 최신순으로. idx_agent_conversations_user_recent 를 그대로 탄다.
    // 패널의 "최근 대화" 3건과 "전체보기"가 Pageable 크기만 바꿔 같은 쿼리를 쓴다.
    List<AgentConversationEntity> findByUserIdOrderByLastMessageAtDesc(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversationEntity c where c.id = :id")
    Optional<AgentConversationEntity> findByIdForUpdate(@Param("id") Long id);
}
