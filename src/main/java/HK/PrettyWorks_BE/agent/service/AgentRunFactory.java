package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRunFactory {
    private static final long MAX_ACTIVE_RUNS_PER_USER = 3;

    private final CurrentUserService currentUserService;
    private final AgentConversationRepository conversationRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentAccessGuard accessGuard;

    @Transactional
    public StartedRun start(Long userId, Long conversationId, String goal,
                            String screenContext, String sessionId) {
        currentUserService.getEmployedUserForUpdate(userId);

        LocalDateTime now = LocalDateTime.now();
        // 기존 대화가 없으면 새 대화 생성
        AgentConversationEntity conversation = conversationId == null
                ? conversationRepository.save(AgentConversationEntity.builder()
                        .userId(userId)
                        .title(fallbackTitle(goal))
                        .lastMessageAt(now)
                        .autoApprove(false)
                        .build())
                : accessGuard.conversationForUpdate(conversationId, userId);

        if (runRepository.existsByConversationIdAndStatusIn(
                conversation.getId(), AgentRunStatus.activeStatuses())) {
            throw BaseException.type(AgentErrorCode.RUN_IN_PROGRESS);
        }
        if (runRepository.countByUserIdAndStatusIn(userId, AgentRunStatus.activeStatuses())
                >= MAX_ACTIVE_RUNS_PER_USER) {
            throw BaseException.type(AgentErrorCode.TOO_MANY_RUNS);
        }

        AgentRunEntity run = runRepository.save(AgentRunEntity.builder()
                .runId(UUID.randomUUID().toString())
                .conversationId(conversation.getId())
                .userId(userId)
                .sessionId(sessionId)
                .goal(goal)
                .screenContext(screenContext)
                .startedAt(now)
                .build());
        AgentMessageEntity userMessage = messageRepository.save(AgentMessageEntity.builder()
                .conversationId(conversation.getId())
                .runId(run.getId())
                .role(AgentRole.USER)
                .content(goal)
                .build());
        conversation.touch(now);
        return new StartedRun(conversation, run, userMessage);
    }

    private String fallbackTitle(String goal) {
        return goal == null || goal.isBlank() ? "새 에이전트 대화" : goal;
    }

    public record StartedRun(AgentConversationEntity conversation, AgentRunEntity run,
                             AgentMessageEntity userMessage) {}
}
