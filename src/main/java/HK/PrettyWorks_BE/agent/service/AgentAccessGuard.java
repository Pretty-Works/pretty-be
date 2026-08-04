package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentAccessGuard {
    private final AgentConversationRepository conversationRepository;
    private final AgentRunRepository runRepository;
    private final AgentInteractionRepository interactionRepository;
    private final AgentMessageRepository messageRepository;

    public AgentConversationEntity conversation(Long conversationId, Long userId) {
        AgentConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));
        requireConversationOwner(conversation, userId);
        return conversation;
    }

    @Transactional
    public AgentConversationEntity conversationForUpdate(Long conversationId, Long userId) {
        AgentConversationEntity conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));
        requireConversationOwner(conversation, userId);
        return conversation;
    }

    private void requireConversationOwner(AgentConversationEntity conversation, Long userId) {
        if (!conversation.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_CONVERSATION);
        }
    }

    public AgentRunEntity run(String publicRunId, Long userId) {
        AgentRunEntity run = runRepository.findByRunId(publicRunId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        if (!run.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_RUN);
        }
        return run;
    }

    public AgentInteractionEntity interaction(Long interactionId, Long userId) {
        AgentInteractionEntity interaction = interactionRepository.findById(interactionId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND));
        AgentRunEntity run = runRepository.findById(interaction.getRunId())
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        if (!run.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_RUN);
        }
        if (interaction.getResolvedAt() == null && !interaction.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw BaseException.type(AgentErrorCode.INTERACTION_EXPIRED);
        }
        return interaction;
    }

    // v1 메시지 승인 경로도 v2 전환이 끝날 때까지 같은 존재→소유권 순서를 사용한다.
    public AgentMessageEntity message(Long messageId, Long userId) {
        AgentMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.MESSAGE_NOT_FOUND));
        conversation(message.getConversationId(), userId);
        return message;
    }
}
