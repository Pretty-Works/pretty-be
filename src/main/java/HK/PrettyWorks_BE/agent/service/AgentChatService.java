package HK.PrettyWorks_BE.agent.service;

import HK.PrettyWorks_BE.agent.client.AgentServerClient;
import HK.PrettyWorks_BE.agent.client.dto.AgentGoalRequest;
import HK.PrettyWorks_BE.agent.client.dto.AgentRawResponse;
import HK.PrettyWorks_BE.agent.constant.AgentActionStatus;
import HK.PrettyWorks_BE.agent.constant.AgentRole;
import HK.PrettyWorks_BE.agent.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.dto.res.AgentMessageResponse;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentContextRow;
import HK.PrettyWorks_BE.agent.repository.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.repository.AgentMessageRepository;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.idempotency.service.IdempotencyService;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 에이전트 채팅 전송 처리.
//
// 이 클래스는 @Transactional을 걸지 않고 트랜잭션 경계를 직접 잡는다(IdempotencyService와 같은 방식).
// 이유가 둘이다.
//   1. FastAPI 호출은 20~60초가 걸린다. 트랜잭션 안에 두면 그동안 DB 커넥션이 잡혀 있다.
//   2. 호출이 실패해도 대화 이력은 남아야 하는데, 한 트랜잭션이면 예외와 함께 롤백된다.
//
// 그래서 순서가 "호출 먼저, 저장 나중"이다. 실패하면 실패 내용을 담은 이력을 별도 트랜잭션으로
// 남기고 나서 예외를 던진다.
@Slf4j
@Service
public class AgentChatService {

    private static final String HTTP_METHOD = "POST";
    private static final String API_PATH = "/api/v1/agent/messages";
    private static final String ENDPOINT = HTTP_METHOD + " " + API_PATH;

    private static final String REQUEST_SOURCE_WEB = "WEB";
    private static final String LOCALE_KO = "ko-KR";

    // 에이전트가 제목을 만들어 주지 못했을 때 첫 질문에서 잘라 쓸 길이.
    private static final int TITLE_FALLBACK_LENGTH = 30;

    private final AgentServerClient agentServerClient;
    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final AgentJsonSupport json;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final int maxContextMessages;
    private final int maxScreenContextBytes;

    public AgentChatService(
            AgentServerClient agentServerClient,
            AgentConversationRepository conversationRepository,
            AgentMessageRepository messageRepository,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            AgentJsonSupport json,
            ObjectMapper objectMapper,
            PlatformTransactionManager txManager,
            @Value("${agent.context.max-messages}") int maxContextMessages,
            @Value("${agent.context.max-screen-bytes}") int maxScreenContextBytes
    ) {
        this.agentServerClient = agentServerClient;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.json = json;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.maxContextMessages = maxContextMessages;
        this.maxScreenContextBytes = maxScreenContextBytes;
    }

    public AgentMessageResponse send(Long userId, String idempotencyKey, AgentMessageRequest request) {
        // 1) 퇴사자 차단. 토큰은 stateless라 퇴사 처리 후에도 만료까지 살아 있다.
        //    에이전트는 프로젝트·예산·회의록을 가로질러 읽어 요약하므로 조회 중에서도 특히 민감하다.
        currentUserService.getEmployedUser(userId);

        // 2) 화면 정보 크기 검사. 내용은 해석하지 않으므로 필드 검증이 불가능해 길이로만 막는다.
        validateScreenContextSize(request.screenContext());

        // 3) 스레드 확보. 새 대화면 null이고 저장 단계에서 만든다.
        //    기존 대화면 소유권(AGENT_001)과 잠금(AGENT_004)을 여기서 검사한다.
        AgentConversationEntity conversation = loadConversationForWrite(userId, request);

        // 4) 대화 맥락. 새 대화는 비어 있다.
        List<AgentGoalRequest.ContextMessage> context = conversation == null
                ? List.of()
                : loadContext(conversation.getId());

        // 5) 에이전트 호출 — 트랜잭션 밖에서 한다.
        AgentRawResponse raw;
        try {
            raw = agentServerClient.execute(toGoalRequest(userId, conversation, request, context));
        } catch (BaseException exception) {
            // 실패해도 이력은 남긴다. 안 남기면 스레드에 답 없는 질문만 남아 채팅 화면이 깨진다.
            // 멱등 키는 기록하지 않는다 — 기록하면 재시도가 "이미 처리됨"으로 걸려 영영 답을 못 받는다.
            saveFailure(userId, conversation, request, exception);
            throw exception;
        }

        // 6) 저장. 멱등 키가 있으면 이 블록 전체가 한 번만 실행된다.
        //    네트워크가 끊겨 클라이언트가 재시도해도 LLM을 다시 부르지 않고 첫 결과를 재생한다.
        Long messageId = idempotencyService.run(
                idempotencyKey,
                ENDPOINT,
                userId,
                idempotencyService.fingerprint(HTTP_METHOD, API_PATH, request),
                () -> saveSuccess(userId, conversation, request, raw)
        );

        // 7) 응답 구성. 재시도로 기존 messageId를 돌려받은 경우와 경로를 하나로 합치려고
        //    방금 저장한 값을 쓰지 않고 id로 다시 읽는다.
        return buildResponse(messageId);
    }

    // ── 검증 ────────────────────────────────────────────────────────────────

    private void validateScreenContextSize(JsonNode screenContext) {
        if (screenContext == null) {
            return;
        }

        int size = objectMapper.writeValueAsString(screenContext).getBytes(StandardCharsets.UTF_8).length;

        if (size > maxScreenContextBytes) {
            log.warn("[에이전트] 화면 정보가 한도를 넘었습니다. size={} limit={}", size, maxScreenContextBytes);
            throw BaseException.type(AgentErrorCode.SCREEN_CONTEXT_TOO_LARGE);
        }
    }

    private AgentConversationEntity loadConversationForWrite(Long userId, AgentMessageRequest request) {
        if (request.conversationId() == null) {
            return null;
        }

        AgentConversationEntity conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));

        // 존재 확인이 먼저다. 소유권을 먼저 보면 없는 대화가 404가 아니라 403으로 나간다.
        if (!conversation.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_CONVERSATION);
        }

        validateThreadLock(conversation.getId(), request.resolvesMessageId());

        return conversation;
    }

    // 승인 대기 중인 제안이 있으면 새 메시지를 막는다.
    // 단 그 제안에 대한 응답(선택지 버튼)은 통과시킨다 — 막으면 선택하려고 메시지를 보내야 하는데
    // 그 메시지가 막혀서 영영 고를 수 없는 상태가 된다.
    private void validateThreadLock(Long conversationId, Long resolvesMessageId) {
        AgentMessageEntity pending = messageRepository
                .findFirstByConversationIdAndActionStatus(conversationId, AgentActionStatus.PENDING)
                .orElse(null);

        if (pending == null) {
            // 대기 중인 게 없는데 무언가에 답하려 한다면, 그 사이 다른 창에서 이미 처리된 것이다.
            if (resolvesMessageId != null) {
                throw BaseException.type(AgentErrorCode.ACTION_ALREADY_RESOLVED);
            }
            return;
        }

        if (resolvesMessageId == null) {
            throw BaseException.type(AgentErrorCode.APPROVAL_PENDING);
        }

        // 화면이 오래된 상태에서 지난 제안에 답하려는 경우.
        if (!pending.getId().equals(resolvesMessageId)) {
            throw BaseException.type(AgentErrorCode.ACTION_ALREADY_RESOLVED);
        }
    }

    // ── 에이전트 호출 준비 ──────────────────────────────────────────────────

    private AgentGoalRequest toGoalRequest(
            Long userId,
            AgentConversationEntity conversation,
            AgentMessageRequest request,
            List<AgentGoalRequest.ContextMessage> context
    ) {
        return AgentGoalRequest.builder()
                .userId(userId)
                .conversationId(conversation == null ? null : conversation.getId())
                .goal(request.goal())
                .messages(context)
                .screenContext(request.screenContext())
                .requestSource(REQUEST_SOURCE_WEB)
                .locale(LOCALE_KO)
                .build();
    }

    // 최신순으로 N건을 뽑아 오래된 순으로 뒤집어 보낸다. LLM은 시간 순서대로 읽어야 맥락이 맞는다.
    private List<AgentGoalRequest.ContextMessage> loadContext(Long conversationId) {
        List<AgentContextRow> rows = messageRepository.findRecentContext(
                conversationId, PageRequest.of(0, maxContextMessages));

        List<AgentGoalRequest.ContextMessage> context = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            AgentContextRow row = rows.get(i);
            context.add(new AgentGoalRequest.ContextMessage(row.role().name(), row.content()));
        }

        return context;
    }

    // ── 저장 ────────────────────────────────────────────────────────────────

    // 질문·답변·스레드 갱신을 한 트랜잭션에 묶는다. 멱등 키가 있으면 키 기록도 여기 함께 커밋된다.
    // 반환값은 AGENT 메시지 id — 재시도 때 이 값으로 첫 응답을 되살린다.
    private Long saveSuccess(
            Long userId,
            AgentConversationEntity detached,
            AgentMessageRequest request,
            AgentRawResponse raw
    ) {
        LocalDateTime now = LocalDateTime.now();
        AgentConversationEntity conversation = attachOrCreate(userId, detached, request.goal(), raw, now);

        // 선택지에 답한 경우 그 제안을 확정한다. 유효성은 validateThreadLock에서 이미 확인했다.
        if (detached != null && request.resolvesMessageId() != null) {
            messageRepository.findById(request.resolvesMessageId())
                    .ifPresent(pending -> pending.resolveAction(AgentActionStatus.APPROVED));
        }

        messageRepository.save(userMessage(conversation.getId(), request.goal()));

        JsonNode action = raw.action();
        AgentMessageEntity answer = messageRepository.save(AgentMessageEntity.builder()
                .conversationId(conversation.getId())
                .role(AgentRole.AGENT)
                .content(raw.answer())
                // 에이전트가 값을 주지 않았으면 성공으로 본다. 여기까지 왔다는 건 답변이 있다는 뜻이다.
                .success(raw.success() == null || raw.success())
                .stepsJson(json.write(raw.steps()))
                .actionType(json.actionType(action))
                .actionStatus(json.requiresApproval(action) ? AgentActionStatus.PENDING : null)
                .actionJson(json.write(action))
                .rawJson(json.write(raw))
                .build());

        conversation.touch(now);

        return answer.getId();
    }

    // 호출이 실패했을 때의 이력. 원래 예외를 가리면 안 되므로 여기서 나는 문제는 로그만 남긴다.
    private void saveFailure(
            Long userId,
            AgentConversationEntity detached,
            AgentMessageRequest request,
            BaseException error
    ) {
        try {
            txTemplate.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now();
                AgentConversationEntity conversation = attachOrCreate(userId, detached, request.goal(), null, now);

                messageRepository.save(userMessage(conversation.getId(), request.goal()));
                messageRepository.save(AgentMessageEntity.builder()
                        .conversationId(conversation.getId())
                        .role(AgentRole.AGENT)
                        .content(error.getCode().getMessage())
                        .success(false)
                        .build());

                conversation.touch(now);
            });
        } catch (RuntimeException exception) {
            log.error("[에이전트] 실패 이력을 남기지 못했습니다.", exception);
        }
    }

    // 기존 스레드는 이 트랜잭션에 다시 붙이고, 새 스레드는 만든다.
    // 트랜잭션 밖에서 조회한 엔티티는 준영속이라 그대로 두면 변경이 반영되지 않는다.
    private AgentConversationEntity attachOrCreate(
            Long userId,
            AgentConversationEntity detached,
            String goal,
            AgentRawResponse raw,
            LocalDateTime now
    ) {
        if (detached != null) {
            return conversationRepository.findById(detached.getId())
                    .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));
        }

        // 제목은 에이전트가 요약해 준 값을 쓰고, 없거나 호출이 실패했으면 첫 질문 앞부분으로 폴백한다.
        // title이 NOT NULL이라 폴백이 반드시 있어야 한다.
        String title = raw != null && raw.conversationTitle() != null && !raw.conversationTitle().isBlank()
                ? raw.conversationTitle()
                : fallbackTitle(goal);

        return conversationRepository.save(AgentConversationEntity.builder()
                .userId(userId)
                .title(title)
                .lastMessageAt(now)
                .build());
    }

    private String fallbackTitle(String goal) {
        String trimmed = goal.trim();

        return trimmed.length() <= TITLE_FALLBACK_LENGTH
                ? trimmed
                : trimmed.substring(0, TITLE_FALLBACK_LENGTH);
    }

    private AgentMessageEntity userMessage(Long conversationId, String goal) {
        return AgentMessageEntity.builder()
                .conversationId(conversationId)
                .role(AgentRole.USER)
                .content(goal)
                .build();
    }

    // ── 응답 ────────────────────────────────────────────────────────────────

    private AgentMessageResponse buildResponse(Long messageId) {
        AgentMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.MESSAGE_NOT_FOUND));

        return AgentMessageResponse.builder()
                .conversationId(message.getConversationId())
                .messageId(message.getId())
                .answer(message.getContent())
                .success(Boolean.TRUE.equals(message.getSuccess()))
                .steps(json.read(message.getStepsJson()))
                .action(json.read(message.getActionJson()))
                .build();
    }
}
