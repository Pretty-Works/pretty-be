package HK.PrettyWorks_BE.agent.conversation.application;

import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationListResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageAttachmentRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageStepRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentConversationRunRow;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentLastAgentMessageRow;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentMessageAttachmentRow;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentMessageRow;
import HK.PrettyWorks_BE.agent.conversation.persistence.projection.AgentMessageStepRow;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.projection.AgentEventSeqRow;
import HK.PrettyWorks_BE.agent.interaction.api.response.AgentPendingInteractionsResponse;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingApprovalRow;
import HK.PrettyWorks_BE.agent.interaction.persistence.projection.AgentPendingInteractionRow;
import HK.PrettyWorks_BE.agent.shared.json.AgentJsonSupport;
import HK.PrettyWorks_BE.agent.shared.security.AgentAccessGuard;
import HK.PrettyWorks_BE.global.base.CursorResponse;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.time.LocalDateTime;

// 에이전트 화면 조회 전용. 데이터를 바꾸지 않으므로 전부 readOnly다.
@Service
@RequiredArgsConstructor
public class AgentQueryService {

    // 승인 카드에 seq를 붙일 때 읽는 이벤트 종류.
    private static final String APPROVAL_REQUEST_EVENT = "approval_request";

    // 승인 카드의 승인·거절 버튼. 에이전트의 alternatives와 id가 겹치지 않는 예약어다.
    private static final String APPROVE_OPTION_ID = "APPROVE";
    private static final String REJECT_OPTION_ID = "REJECT";
    private static final String APPROVE_OPTION_LABEL = "승인";
    private static final String REJECT_OPTION_LABEL = "거절";

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentMessageStepRepository messageStepRepository;
    private final AgentMessageAttachmentRepository messageAttachmentRepository;
    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final AgentInteractionRepository interactionRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentJsonSupport json;
    private final CurrentUserService currentUserService;

    // 대화 내역(햄버거). 패널의 "최근 대화 3건"과 "전체보기"가 size만 달리 해서 같은 API를 쓴다.
    //
    // 커서 방식이다. 대화는 답변이 오갈 때마다 맨 위로 올라오므로, page 번호로 끊으면
    // 스크롤 도중 순서가 밀려 이미 본 대화가 다음 페이지에 또 오거나 아직 못 본 대화가 통째로 빠진다.
    // 전체 개수도 세지 않는다 — 무한 스크롤에는 쓸 데가 없고 count 쿼리만 남는다.
    @Transactional(readOnly = true)
    public CursorResponse<AgentConversationListResponse, String> getConversations(
            Long userId, String cursor, int size) {
        currentUserService.getEmployedUser(userId);
        // size 범위 검증만 재사용한다(REQUEST_001). 여기서 만든 PageRequest는 쓰지 않는다 —
        // hasNext 판정에 한 건을 더 가져오므로 size가 상한이면 size+1이 상한을 넘어 거부된다.
        PageRequests.of(0, size);

        AgentConversationCursor from = AgentConversationCursor.decode(cursor);
        List<AgentConversationEntity> fetched = conversationRepository.findScroll(
                userId, from.lastMessageAt(), from.id(), PageRequest.ofSize(size + 1));
        boolean hasNext = fetched.size() > size;
        List<AgentConversationEntity> conversations = hasNext ? fetched.subList(0, size) : fetched;

        List<Long> conversationIds = conversations.stream()
                .map(AgentConversationEntity::getId)
                .toList();
        Map<Long, AgentConversationRunRow> latestRuns = latestRuns(conversationIds);
        Map<Long, Long> pendingApprovals = pendingApprovals(latestRuns.values());
        Map<Long, Long> lastAgentMessages = lastAgentMessages(conversationIds);

        List<AgentConversationListResponse> items = conversations.stream()
                .map(conversation -> {
                    AgentConversationRunRow run = latestRuns.get(conversation.getId());
                    return AgentConversationListResponse.builder()
                            .conversationId(conversation.getId())
                            .title(conversation.getTitle())
                            .status(run == null ? null : run.status())
                            .runId(run == null ? null : run.runId())
                            .pendingApprovalId(run == null ? null : pendingApprovals.get(run.runInternalId()))
                            .unread(conversation.isUnread(lastAgentMessages.get(conversation.getId())))
                            .lastMessageAt(conversation.getLastMessageAt())
                            .createdAt(conversation.getCreatedAt())
                            .build();
                })
                .toList();

        // 커서는 마지막 항목의 (lastMessageAt, id)다. 목록이 비면 이어받을 지점이 없다.
        String nextCursor = conversations.isEmpty()
                ? null : AgentConversationCursor.encode(conversations.getLast());

        return new CursorResponse<>(items, nextCursor, hasNext);
    }

    // 쿼리가 대화별 id 내림차순이라 처음 만난 행이 그 대화의 최신 실행이다.
    private Map<Long, AgentConversationRunRow> latestRuns(List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AgentConversationRunRow> latest = new LinkedHashMap<>();
        for (AgentConversationRunRow row
                : runRepository.findRunRowsByConversationIdIn(conversationIds)) {
            latest.putIfAbsent(row.conversationId(), row);
        }
        return latest;
    }

    // 안 읽음 판정에 쓸 마지막 AGENT 메시지 id. 답변이 한 번도 없던 대화는 여기서 빠지고
    // isUnread(null) — 즉 읽은 상태가 된다.
    private Map<Long, Long> lastAgentMessages(List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> lastIds = new LinkedHashMap<>();
        for (AgentLastAgentMessageRow row
                : messageRepository.findLastAgentMessageRows(conversationIds, AgentRole.AGENT)) {
            lastIds.put(row.conversationId(), row.lastAgentMessageId());
        }
        return lastIds;
    }

    // 최신 실행에 달린 대기 승인만 본다. 한 실행에 대기 카드가 둘일 수 없지만 방어적으로 첫 건만 쓴다.
    private Map<Long, Long> pendingApprovals(Collection<AgentConversationRunRow> runs) {
        List<Long> runIds = runs.stream()
                .map(AgentConversationRunRow::runInternalId)
                .toList();
        if (runIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> approvals = new LinkedHashMap<>();
        for (AgentPendingApprovalRow row : interactionRepository.findPendingApprovalRows(
                runIds, AgentInteractionKind.APPROVAL, AgentInteractionStatus.PENDING)) {
            approvals.putIfAbsent(row.runInternalId(), row.approvalId());
        }
        return approvals;
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
        Map<Long, List<AgentMessagesResponse.AttachmentItem>> attachmentsByMessage =
                loadAttachments(rows);

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
                                .attachments(attachmentsByMessage.get(row.messageId()))
                                .createdAt(row.createdAt())
                                .build())
                        .toList())
                .approvals(approvals(conversationId))
                .build();
    }

    // 지난 승인 카드. 답이 끝난 것도 결과와 함께 남겨 말풍선 사이에 다시 그릴 수 있게 한다.
    private List<AgentMessagesResponse.ApprovalItem> approvals(Long conversationId) {
        List<AgentApprovalRow> rows = interactionRepository.findApprovalRows(
                conversationId, AgentInteractionKind.APPROVAL);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> seqs = approvalSeqs(rows.stream()
                .map(AgentApprovalRow::runInternalId)
                .distinct()
                .toList());

        return rows.stream()
                .map(row -> AgentMessagesResponse.ApprovalItem.builder()
                        .approvalId(row.approvalId())
                        .seq(seqs.get(row.approvalId()))
                        .access(row.access())
                        .summary(row.label())
                        .previewText(row.previewText())
                        .alternatives(alternatives(json.read(row.payloadJson())))
                        .status(row.status())
                        .chosenAlternativeId(row.alternativeId())
                        .decidedAt(row.resolvedAt())
                        .build())
                .toList();
    }

    // 이벤트와 카드를 잇는 FK가 없어 payload의 approvalId로 맞춘다.
    // 이벤트가 이미 정리된 오래된 카드는 여기서 빠지고 seq가 null이 된다.
    private Map<Long, Long> approvalSeqs(List<Long> runIds) {
        Map<Long, Long> seqs = new LinkedHashMap<>();
        for (AgentEventSeqRow row : eventRepository.findSeqRows(runIds, APPROVAL_REQUEST_EVENT)) {
            JsonNode payload = json.read(row.payload());
            JsonNode approvalId = payload == null ? null : payload.get("approvalId");
            if (approvalId != null && approvalId.isNumber()) {
                seqs.putIfAbsent(approvalId.longValue(), row.seq());
            }
        }
        return seqs;
    }

    private List<AgentMessagesResponse.Alternative> alternatives(JsonNode payload) {
        return readOptions(payload == null ? null : payload.get("alternatives")).stream()
                .map(option -> new AgentMessagesResponse.Alternative(option.id(), option.label()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentPendingInteractionsResponse getPendingInteractions(Long userId) {
        currentUserService.getEmployedUser(userId);
        var items = interactionRepository.findPendingRows(
                        userId, AgentInteractionStatus.PENDING,
                        java.util.EnumSet.of(AgentRunStatus.WAITING_APPROVAL,
                                AgentRunStatus.WAITING_INPUT), LocalDateTime.now())
                .stream()
                .map(this::toPendingInteraction)
                .toList();
        return new AgentPendingInteractionsResponse(items.size(), items);
    }

    // 카드 원문(payload)을 통째로 내려보내지 않고 화면이 바로 그릴 수 있는 필드로 펼친다.
    private AgentPendingInteractionsResponse.PendingInteraction toPendingInteraction(
            AgentPendingInteractionRow row) {
        JsonNode payload = json.read(row.payloadJson());
        return new AgentPendingInteractionsResponse.PendingInteraction(
                row.kind(), row.interactionId(), row.label(),
                options(row.kind(), payload), multiple(payload),
                row.conversationId(), row.publicRunId(), row.conversationTitle(),
                previewText(payload), row.createdAt(), row.expiresAt());
    }

    // 에이전트가 options를 보내오면 손대지 않고 그대로 통과시킨다(AgentJsonSupport 주석의 통짜 보존 원칙).
    // 아직 alternatives만 보내는 승인 카드는 승인·거절 버튼을 앞뒤에 채워 같은 모양으로 맞춘다.
    private List<AgentPendingInteractionsResponse.Option> options(AgentInteractionKind kind,
                                                                 JsonNode payload) {
        if (payload == null) {
            return List.of();
        }
        List<AgentPendingInteractionsResponse.Option> supplied = readOptions(payload.get("options"));
        if (!supplied.isEmpty() || kind != AgentInteractionKind.APPROVAL) {
            return supplied;
        }

        List<AgentPendingInteractionsResponse.Option> options = new ArrayList<>();
        options.add(new AgentPendingInteractionsResponse.Option(
                APPROVE_OPTION_ID, APPROVE_OPTION_LABEL));
        options.addAll(readOptions(payload.get("alternatives")));
        options.add(new AgentPendingInteractionsResponse.Option(
                REJECT_OPTION_ID, REJECT_OPTION_LABEL));
        return List.copyOf(options);
    }

    // id 없는 선택지는 되돌려 보낼 수 없으므로 버린다.
    private List<AgentPendingInteractionsResponse.Option> readOptions(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<AgentPendingInteractionsResponse.Option> options = new ArrayList<>();
        for (JsonNode option : array) {
            JsonNode id = option.get("id");
            if (id == null || !id.isTextual() || id.textValue().isBlank()) {
                continue;
            }
            JsonNode label = option.get("label");
            options.add(new AgentPendingInteractionsResponse.Option(id.textValue(),
                    label != null && label.isTextual() ? label.textValue() : null));
        }
        return List.copyOf(options);
    }

    private boolean multiple(JsonNode payload) {
        JsonNode value = payload == null ? null : payload.get("multiple");
        return value != null && value.isBoolean() && value.booleanValue();
    }

    private String previewText(JsonNode payload) {
        JsonNode value = payload == null ? null : payload.get("previewText");
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private AgentRunEntity singleActiveRun(List<AgentRunEntity> runs) {
        if (runs.size() > 1) {
            throw new IllegalStateException("한 대화에 활성 에이전트 Run이 둘 이상 존재합니다.");
        }
        return runs.isEmpty() ? null : runs.getFirst();
    }

    // step과 같은 방식으로 한 번에 모은다. 첨부는 대부분의 말풍선에 없으므로 조회 결과도 대개 비어 있다.
    private Map<Long, List<AgentMessagesResponse.AttachmentItem>> loadAttachments(
            List<AgentMessageRow> messages) {
        List<Long> messageIds = messages.stream()
                .map(AgentMessageRow::messageId)
                .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AgentMessagesResponse.AttachmentItem>> grouped = new LinkedHashMap<>();
        for (AgentMessageAttachmentRow row
                : messageAttachmentRepository.findRowsByMessageIdIn(messageIds)) {
            grouped.computeIfAbsent(row.messageId(), ignored -> new ArrayList<>())
                    .add(new AgentMessagesResponse.AttachmentItem(
                            row.filename(), row.contentType(), row.sizeBytes()));
        }
        return grouped;
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
