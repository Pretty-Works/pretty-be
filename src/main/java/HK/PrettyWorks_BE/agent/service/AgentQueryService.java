package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.constant.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.constant.AgentRunStatus;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationsResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentPendingInteractionsResponse;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRow;
import HK.PrettyWorks_BE.agent.repository.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageStepRow;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

// 에이전트 화면 조회 전용. 데이터를 바꾸지 않으므로 전부 readOnly다.
@Service
@RequiredArgsConstructor
public class AgentQueryService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentMessageStepRepository messageStepRepository;
    private final AgentRunRepository runRepository;
    private final AgentInteractionRepository interactionRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentJsonSupport json;
    private final CurrentUserService currentUserService;

    // 대화 내역(햄버거). 패널의 "최근 대화 3건"과 "전체보기"가 size만 달리 해서 같은 API를 쓴다.
    @Transactional(readOnly = true)
    public AgentConversationsResponse getConversations(Long userId, int size) {
        currentUserService.getEmployedUser(userId);
        // size 범위 검증은 PageRequests가 한다(REQUEST_001).
        List<AgentConversationEntity> conversations =
                conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId, PageRequests.of(0, size));
        Map<Long, AgentRunEntity> activeRuns = activeRuns(conversations.stream()
                .map(AgentConversationEntity::getId)
                .toList());

        return AgentConversationsResponse.builder()
                .conversations(conversations.stream()
                        .map(conversation -> toConversationItem(
                                conversation, activeRuns.get(conversation.getId())))
                        .toList())
                .build();
    }

    // 스레드 상세. 프로젝션으로 조회해 rawJson(LONGTEXT)을 끌고 오지 않는다.
    @Transactional(readOnly = true)
    public AgentMessagesResponse getMessages(Long userId, Long conversationId) {
        currentUserService.getEmployedUser(userId);
        AgentConversationEntity conversation = accessGuard.conversation(conversationId, userId);
        AgentRunEntity activeRun = singleActiveRun(runRepository
                .findByConversationIdAndStatusIn(
                        conversationId, AgentRunStatus.activeStatuses()));

        List<AgentMessageRow> rows = messageRepository.findMessageRows(conversationId);
        Map<Long, List<String>> stepPayloadsByMessage = loadStepPayloads(rows);

        return AgentMessagesResponse.builder()
                .conversationId(conversation.getId())
                .title(conversation.getTitle())
                .autoApprove(conversation.isAutoApprove())
                .activeRunId(activeRun == null ? null : activeRun.getRunId())
                .activeRunStatus(activeRun == null ? null : activeRun.getStatus())
                .messages(rows.stream()
                        .map(row -> AgentMessagesResponse.MessageItem.builder()
                                .messageId(row.messageId())
                                .runId(row.runId())
                                .role(row.role())
                                .content(row.content())
                                .success(row.success())
                                .steps(json.readArray(stepPayloadsByMessage.get(row.messageId())))
                                .actionType(row.actionType())
                                .action(json.read(row.actionJson()))
                                .createdAt(row.createdAt())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public AgentPendingInteractionsResponse getPendingInteractions(Long userId) {
        currentUserService.getEmployedUser(userId);
        var interactions = interactionRepository.findPendingRows(
                        userId, AgentInteractionStatus.PENDING,
                        java.util.EnumSet.of(AgentRunStatus.WAITING_APPROVAL,
                                AgentRunStatus.WAITING_INPUT), LocalDateTime.now())
                .stream()
                .map(row -> new AgentPendingInteractionsResponse.PendingInteraction(
                        row.interactionId(), row.kind(), row.label(), json.read(row.payloadJson()),
                        row.publicRunId(), row.conversationId(), row.conversationTitle(),
                        row.expiresAt(), row.createdAt()))
                .toList();
        return new AgentPendingInteractionsResponse(interactions.size(), interactions);
    }

    private AgentConversationsResponse.ConversationItem toConversationItem(
            AgentConversationEntity conversation, AgentRunEntity activeRun) {
        return AgentConversationsResponse.ConversationItem.builder()
                .conversationId(conversation.getId())
                .title(conversation.getTitle())
                .lastMessageAt(conversation.getLastMessageAt())
                .autoApprove(conversation.isAutoApprove())
                .activeRunId(activeRun == null ? null : activeRun.getRunId())
                .activeRunStatus(activeRun == null ? null : activeRun.getStatus())
                .build();
    }

    private Map<Long, AgentRunEntity> activeRuns(List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return runRepository.findByConversationIdInAndStatusIn(
                        conversationIds, AgentRunStatus.activeStatuses())
                .stream()
                .collect(Collectors.toMap(AgentRunEntity::getConversationId,
                        Function.identity(), (first, duplicate) -> {
                            throw new IllegalStateException(
                                    "한 대화에 활성 에이전트 Run이 둘 이상 존재합니다.");
                        }));
    }

    private AgentRunEntity singleActiveRun(List<AgentRunEntity> runs) {
        if (runs.size() > 1) {
            throw new IllegalStateException("한 대화에 활성 에이전트 Run이 둘 이상 존재합니다.");
        }
        return runs.isEmpty() ? null : runs.getFirst();
    }

    private Map<Long, List<String>> loadStepPayloads(List<AgentMessageRow> messages) {
        List<Long> messageIds = messages.stream()
                .map(AgentMessageRow::messageId)
                .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (AgentMessageStepRow step : messageStepRepository.findRowsByMessageIdIn(messageIds)) {
            grouped.computeIfAbsent(step.messageId(), ignored -> new ArrayList<>())
                    .add(step.payload());
        }
        return grouped;
    }

    // 홈 「확인이 필요한 요청」. 대기는 스레드 단위로 생기지만 여기서는 내 모든 스레드를 합친다.
    // count가 곧 상단 뱃지 숫자라 개수 전용 API를 따로 두지 않았다.
}
