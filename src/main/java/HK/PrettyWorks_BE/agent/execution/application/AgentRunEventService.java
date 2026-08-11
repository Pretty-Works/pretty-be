package HK.PrettyWorks_BE.agent.execution.application;

import HK.PrettyWorks_BE.agent.conversation.application.AgentMessageStepService;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentMessageEntity;
import HK.PrettyWorks_BE.agent.conversation.domain.AgentRole;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentMessageRepository;
import HK.PrettyWorks_BE.agent.execution.domain.AgentEventEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.AgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.gateway.dto.DecodedAgentServerEvent;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentEventRepository;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentEventSignalPublisher;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStreamService;
import HK.PrettyWorks_BE.agent.interaction.application.AgentInteractionService;
import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import HK.PrettyWorks_BE.global.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Applies one validated FastAPI event to the durable Run model.
 *
 * <p>State, interactions, terminal messages, and the replayable SSE event are committed together.
 * Live delivery deliberately happens only after that transaction returns successfully.</p>
 */
@Service
@Slf4j
public class AgentRunEventService {
    private static final String ALWAYS_ID = "ALWAYS";
    private static final String ALWAYS_LABEL = "항상 허용";

    private final AgentRunRepository runRepository;
    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentMessageStepService messageStepService;
    private final AgentEventRepository eventRepository;
    private final AgentInteractionService interactionService;
    private final AgentInteractionRepository interactionRepository;
    private final AgentRunStateMachine stateMachine;
    private final AgentStreamService streamService;
    private final ApprovalTokenService tokenService;
    private final AgentEventSignalPublisher eventSignalPublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AgentRunEventService(AgentRunRepository runRepository,
                                AgentConversationRepository conversationRepository,
                                AgentMessageRepository messageRepository,
                                AgentMessageStepService messageStepService,
                                AgentEventRepository eventRepository,
                                AgentInteractionService interactionService,
                                AgentInteractionRepository interactionRepository,
                                AgentRunStateMachine stateMachine,
                                AgentStreamService streamService,
                                ApprovalTokenService tokenService,
                                AgentEventSignalPublisher eventSignalPublisher,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageStepService = messageStepService;
        this.eventRepository = eventRepository;
        this.interactionService = interactionService;
        this.interactionRepository = interactionRepository;
        this.stateMachine = stateMachine;
        this.streamService = streamService;
        this.tokenService = tokenService;
        this.eventSignalPublisher = eventSignalPublisher;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public HandlingResult handle(Long internalRunId, DecodedAgentServerEvent decoded) {
        Objects.requireNonNull(internalRunId, "internalRunId");
        Objects.requireNonNull(decoded, "decoded");

        AppliedEvent applied = transactionTemplate.execute(status -> applyLocked(internalRunId, decoded));
        if (applied == null) {
            throw new IllegalStateException("이벤트 적용 결과가 없습니다.");
        }
        deliverCommitted(applied);
        return applied.result();
    }

    /** Converts a transport/parser failure into the same atomic terminal model as an error event. */
    public HandlingResult failRunningRun(Long internalRunId, AgentErrorCode errorCode) {
        Objects.requireNonNull(internalRunId, "internalRunId");
        Objects.requireNonNull(errorCode, "errorCode");

        AppliedEvent applied = transactionTemplate.execute(status -> {
            AgentRunEntity run = lockedRun(internalRunId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return AppliedEvent.ignored();
            }
            ObjectNode payload = errorPayload(errorCode.getErrorCode(), errorCode.getMessage());
            stateMachine.transitionLocked(run, EnumSet.of(AgentRunStatus.RUNNING),
                    AgentRunStatus.FAILED, errorCode.getErrorCode());
            saveTerminalMessage(run, errorCode.getMessage(), false, null, payload);
            return append(run.getId(), "error", payload, HandlingResult.failed());
        });
        if (applied == null) {
            throw new IllegalStateException("실패 이벤트 적용 결과가 없습니다.");
        }
        deliverCommitted(applied);
        return applied.result();
    }

    /** Cancels any active state and revokes approvals before late FastAPI work can write. */
    public HandlingResult cancelActiveRun(Long internalRunId) {
        Objects.requireNonNull(internalRunId, "internalRunId");
        AppliedEvent applied = transactionTemplate.execute(status -> {
            AgentRunEntity run = lockedRun(internalRunId);
            if (!run.getStatus().isActive()) {
                return AppliedEvent.ignored();
            }
            LocalDateTime now = LocalDateTime.now();
            for (AgentInteractionEntity interaction
                    : interactionRepository.findByRunIdOrderById(internalRunId)) {
                if (interaction.getStatus() == AgentInteractionStatus.PENDING) {
                    interaction.expirePending(now);
                } else if (interaction.getStatus() == AgentInteractionStatus.APPROVED
                        && interaction.getExecutedAt() == null) {
                    interaction.revokeToken(now);
                }
            }

            AgentErrorCode error = AgentErrorCode.RUN_CANCELED;
            ObjectNode payload = errorPayload(error.getErrorCode(), error.getMessage());
            stateMachine.transitionLocked(run, AgentRunStatus.activeStatuses(),
                    AgentRunStatus.FAILED, error.getErrorCode());
            saveTerminalMessage(run, error.getMessage(), false, null, payload);
            return append(run.getId(), "error", payload, HandlingResult.failed());
        });
        if (applied == null) {
            throw new IllegalStateException("취소 이벤트 적용 결과가 없습니다.");
        }
        deliverCommitted(applied);
        return applied.result();
    }

    /** Registers an authenticated internal tool attempt and terminates the Run on attempt 21. */
    public void registerToolCall(Long internalRunId) {
        AppliedEvent applied = transactionTemplate.execute(status -> {
            AgentRunEntity run = lockedRun(internalRunId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                throw BaseException.type(AgentErrorCode.RUN_NOT_FOUND);
            }
            if (run.getToolCallCount() < AgentRunStateMachine.MAX_TOOL_CALLS) {
                run.registerToolCall(LocalDateTime.now());
                return AppliedEvent.ignored();
            }

            AgentErrorCode error = AgentErrorCode.TOOL_CALL_LIMIT_EXCEEDED;
            ObjectNode payload = errorPayload(error.getErrorCode(), error.getMessage());
            stateMachine.transitionLocked(run, EnumSet.of(AgentRunStatus.RUNNING),
                    AgentRunStatus.FAILED, error.getErrorCode());
            saveTerminalMessage(run, error.getMessage(), false, null, payload);
            return append(run.getId(), "error", payload, HandlingResult.failed());
        });
        if (applied == null) {
            throw new IllegalStateException("도구 호출 등록 결과가 없습니다.");
        }
        deliverCommitted(applied);
        if (applied.result().disposition() == Disposition.FAILED) {
            throw BaseException.type(AgentErrorCode.TOOL_CALL_LIMIT_EXCEEDED);
        }
    }

    /** Expires one pending interaction together with its waiting Run. Safe under duplicate schedulers. */
    public HandlingResult expireInteraction(Long interactionId, LocalDateTime now) {
        Objects.requireNonNull(interactionId, "interactionId");
        Objects.requireNonNull(now, "now");

        AppliedEvent applied = transactionTemplate.execute(status -> {
            AgentInteractionEntity snapshot = interactionRepository.findById(interactionId)
                    .orElse(null);
            if (snapshot == null) {
                return AppliedEvent.ignored();
            }
            AgentRunEntity run = lockedRun(snapshot.getRunId());
            AgentInteractionEntity interaction = interactionRepository.findByIdForUpdate(interactionId)
                    .orElse(null);
            if (interaction == null
                    || interaction.getStatus() != AgentInteractionStatus.PENDING
                    || interaction.getExpiresAt().isAfter(now)) {
                return AppliedEvent.ignored();
            }

            interaction.expirePending(now);
            AgentRunStatus expected = interaction.getKind() == AgentInteractionKind.APPROVAL
                    ? AgentRunStatus.WAITING_APPROVAL : AgentRunStatus.WAITING_INPUT;
            if (run.getStatus() != expected) {
                return AppliedEvent.ignored();
            }

            AgentErrorCode error = AgentErrorCode.INTERACTION_EXPIRED;
            stateMachine.transitionLocked(run, EnumSet.of(expected),
                    AgentRunStatus.EXPIRED, error.getErrorCode());
            ObjectNode payload = errorPayload(error.getErrorCode(), error.getMessage());
            saveTerminalMessage(run, error.getMessage(), false, null, payload);
            return append(run.getId(), "error", payload, HandlingResult.expired());
        });
        if (applied == null) {
            throw new IllegalStateException("상호작용 만료 결과가 없습니다.");
        }
        deliverCommitted(applied);
        return applied.result();
    }

    public HandlingResult expireTimedOutRunningRun(Long internalRunId, LocalDateTime threshold) {
        Objects.requireNonNull(internalRunId, "internalRunId");
        Objects.requireNonNull(threshold, "threshold");
        AppliedEvent applied = transactionTemplate.execute(status -> {
            AgentRunEntity run = lockedRun(internalRunId);
            if (run.getStatus() != AgentRunStatus.RUNNING
                    || run.getStartedAt().isAfter(threshold)) {
                return AppliedEvent.ignored();
            }
            AgentErrorCode error = AgentErrorCode.AGENT_SERVER_TIMEOUT;
            stateMachine.transitionLocked(run, EnumSet.of(AgentRunStatus.RUNNING),
                    AgentRunStatus.EXPIRED, error.getErrorCode());
            ObjectNode payload = errorPayload(error.getErrorCode(), error.getMessage());
            saveTerminalMessage(run, error.getMessage(), false, null, payload);
            return append(run.getId(), "error", payload, HandlingResult.expired());
        });
        if (applied == null) {
            throw new IllegalStateException("실행 만료 결과가 없습니다.");
        }
        deliverCommitted(applied);
        return applied.result();
    }

    private AppliedEvent applyLocked(Long internalRunId, DecodedAgentServerEvent decoded) {
        AgentRunEntity run = lockedRun(internalRunId);
        if (run.getStatus() != AgentRunStatus.RUNNING) {
            return AppliedEvent.ignored();
        }

        AgentServerEvent event = decoded.event();
        ObjectNode payload = decoded.payload();
        return switch (event) {
            case AgentServerEvent.Step ignored -> applyStep(run, payload);
            case AgentServerEvent.ApprovalRequest approval ->
                    applyApproval(run, approval, payload);
            case AgentServerEvent.Question question ->
                    applyQuestion(run, question, payload);
            case AgentServerEvent.Done done ->
                    applyDone(run, done, payload);
            case AgentServerEvent.Failure failure ->
                    applyFailure(run, failure, payload);
        };
    }

    private AppliedEvent applyStep(AgentRunEntity run, ObjectNode payload) {
        if (eventRepository.countByRunIdAndEventType(run.getId(), "step")
                >= AgentPayloadLimits.MAX_STEPS_PER_RUN) {
            AgentErrorCode error = AgentErrorCode.AGENT_RESPONSE_INVALID;
            ObjectNode errorPayload = errorPayload(error.getErrorCode(), error.getMessage());
            stateMachine.transitionLocked(run, EnumSet.of(AgentRunStatus.RUNNING),
                    AgentRunStatus.FAILED, error.getErrorCode());
            saveTerminalMessage(run, error.getMessage(), false, null, errorPayload);
            return append(run.getId(), "error", errorPayload, HandlingResult.failed());
        }
        return append(run.getId(), "step", payload, HandlingResult.continueRun());
    }

    private AppliedEvent applyApproval(AgentRunEntity run,
                                       AgentServerEvent.ApprovalRequest approval,
                                       ObjectNode payload) {
        AgentConversationEntity conversation = lockedConversation(run.getConversationId());
        boolean autoApproved = conversation.isAutoApprove();
        AgentInteractionEntity interaction = interactionService.createApproval(
                run.getId(), approval.toolCallId(), approval.summary(), approval.tool(),
                approval.params(), null, autoApproved);

        payload.put("approvalId", interaction.getId());
        payload.put("previewText", interaction.getPreviewText());
        payload.put("autoApproved", autoApproved);
        removeReservedAlternative(payload, run.getRunId());

        if (autoApproved) {
            ApprovalTokenService.IssuedToken issued = tokenService.issue(interaction.getId());
            interaction.replaceDisplayPayload(toJson(payload));
            AutoApprovalResume resume = new AutoApprovalResume(
                    interaction.getId(), issued.token(), interaction.getParamsCanonical());
            return append(run.getId(), "approval_request", payload,
                    HandlingResult.autoApproval(resume));
        }

        appendAlwaysAlternative(payload);
        interaction.replaceDisplayPayload(toJson(payload));
        boolean transitioned = stateMachine.transitionLocked(run,
                EnumSet.of(AgentRunStatus.RUNNING), AgentRunStatus.WAITING_APPROVAL, null);
        if (!transitioned) {
            return AppliedEvent.ignored();
        }
        return append(run.getId(), "approval_request", payload,
                HandlingResult.waitingApproval());
    }

    private AppliedEvent applyQuestion(AgentRunEntity run,
                                       AgentServerEvent.Question question,
                                       ObjectNode payload) {
        AgentRunStateMachine.QuestionRegistration registration =
                stateMachine.registerQuestionOrFail(run);
        if (registration == AgentRunStateMachine.QuestionRegistration.LIMIT_EXCEEDED) {
            AgentErrorCode error = AgentErrorCode.TOO_MANY_QUESTIONS;
            ObjectNode errorPayload = errorPayload(error.getErrorCode(), error.getMessage());
            saveTerminalMessage(run, error.getMessage(), false, null, errorPayload);
            return append(run.getId(), "error", errorPayload, HandlingResult.failed());
        }

        AgentInteractionEntity interaction = interactionService.createQuestionAfterRegistration(
                run.getId(), question.label(), question.text(), payload);
        payload.put("questionId", interaction.getId());
        interaction.replaceDisplayPayload(toJson(payload));
        boolean transitioned = stateMachine.transitionLocked(run,
                EnumSet.of(AgentRunStatus.RUNNING), AgentRunStatus.WAITING_INPUT, null);
        if (!transitioned) {
            return AppliedEvent.ignored();
        }
        return append(run.getId(), "question", payload, HandlingResult.waitingInput());
    }

    private AppliedEvent applyDone(AgentRunEntity run, AgentServerEvent.Done done,
                                   ObjectNode payload) {
        boolean transitioned = stateMachine.transitionLocked(run,
                EnumSet.of(AgentRunStatus.RUNNING), AgentRunStatus.COMPLETED, null);
        if (!transitioned) {
            return AppliedEvent.ignored();
        }
        saveTerminalMessage(run, done.answer(), true, done.action(), payload);
        return append(run.getId(), "done", payload, HandlingResult.completed());
    }

    private AppliedEvent applyFailure(AgentRunEntity run, AgentServerEvent.Failure failure,
                                      ObjectNode payload) {
        boolean transitioned = stateMachine.transitionLocked(run,
                EnumSet.of(AgentRunStatus.RUNNING), AgentRunStatus.FAILED, failure.code());
        if (!transitioned) {
            return AppliedEvent.ignored();
        }
        saveTerminalMessage(run, failure.message(), false, null, payload);
        return append(run.getId(), "error", payload, HandlingResult.failed());
    }

    private void saveTerminalMessage(AgentRunEntity run, String content, boolean success,
                                     AgentServerEvent.Action action, ObjectNode rawPayload) {
        AgentConversationEntity conversation = lockedConversation(run.getConversationId());
        String actionType = action == null ? null : action.type();
        String actionJson = action == null || rawPayload.get("action") == null
                ? null : toJson(rawPayload.get("action"));
        logExternalLink(run, action);
        AgentMessageEntity message = messageRepository.save(AgentMessageEntity.builder()
                .conversationId(run.getConversationId())
                .runId(run.getId())
                .role(AgentRole.AGENT)
                .content(content)
                .success(success)
                .actionType(actionType)
                .actionJson(actionJson)
                .rawJson(toJson(rawPayload))
                .build());
        messageStepService.copyFromRun(message.getId(), run.getId());
        conversation.touch(LocalDateTime.now());
    }

    // 채팅에 외부 링크 버튼이 그려진 사실을 남긴다. "이상한 링크가 떴다"는 신고가 들어왔을 때
    // 어느 실행에서 어디로 가는 버튼이 나갔는지 알 방법이 이 줄뿐이다(URL 전체는 남기지 않는다 —
    // 쿼리에 일회용 토큰이 실린다).
    private void logExternalLink(AgentRunEntity run, AgentServerEvent.Action action) {
        if (action == null || !"OPEN_EXTERNAL_URL".equals(action.type())) {
            return;
        }
        JsonNode url = action.params() == null ? null : action.params().get("url");
        log.info("[에이전트 외부 링크] runId={}, url={}", run.getRunId(),
                url == null ? null : URI.create(url.textValue()).getHost());
    }

    private AppliedEvent append(Long internalRunId, String eventType, ObjectNode payload,
                                HandlingResult result) {
        AgentEventEntity persisted = streamService.appendInCurrentTransaction(
                internalRunId, eventType, toJson(payload));
        return new AppliedEvent(result, persisted);
    }

    private void deliverCommitted(AppliedEvent applied) {
        if (applied.event() == null) {
            return;
        }
        streamService.deliver(applied.event());
        eventSignalPublisher.publish(applied.event());
        switch (applied.result().disposition()) {
            case WAITING_APPROVAL, WAITING_INPUT ->
                    streamService.completeSegment(applied.event().getRunId());
            case COMPLETED, FAILED, EXPIRED -> streamService.completeRun(applied.event().getRunId());
            case CONTINUE, AUTO_APPROVAL, IGNORED -> {
            }
        }
    }

    private void appendAlwaysAlternative(ObjectNode payload) {
        ArrayNode alternatives;
        if (payload.get("alternatives") instanceof ArrayNode existing) {
            alternatives = existing;
        } else {
            alternatives = objectMapper.createArrayNode();
            payload.set("alternatives", alternatives);
        }
        ObjectNode always = objectMapper.createObjectNode();
        always.put("id", ALWAYS_ID);
        always.put("label", ALWAYS_LABEL);
        alternatives.add(always);
    }

    private void removeReservedAlternative(ObjectNode payload, String runId) {
        if (!(payload.get("alternatives") instanceof ArrayNode alternatives)) {
            return;
        }
        for (int index = alternatives.size() - 1; index >= 0; index--) {
            JsonNode id = alternatives.get(index).get("id");
            if (id != null && ALWAYS_ID.equals(id.textValue())) {
                alternatives.remove(index);
                log.error("[에이전트 승인] FastAPI가 예약 선택지 ALWAYS를 전송해 폐기했습니다. runId={}",
                        runId);
            }
        }
    }

    private ObjectNode errorPayload(String code, String message) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }

    private AgentRunEntity lockedRun(Long internalRunId) {
        return runRepository.findByIdForUpdate(internalRunId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
    }

    private AgentConversationEntity lockedConversation(Long conversationId) {
        return conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private record AppliedEvent(HandlingResult result, AgentEventEntity event) {
        private static AppliedEvent ignored() {
            return new AppliedEvent(HandlingResult.ignored(), null);
        }
    }

    public enum Disposition {
        CONTINUE,
        WAITING_APPROVAL,
        WAITING_INPUT,
        AUTO_APPROVAL,
        COMPLETED,
        FAILED,
        EXPIRED,
        IGNORED
    }

    public record HandlingResult(Disposition disposition, AutoApprovalResume autoApproval) {
        public HandlingResult {
            Objects.requireNonNull(disposition, "disposition");
            if ((disposition == Disposition.AUTO_APPROVAL) != (autoApproval != null)) {
                throw new IllegalArgumentException("자동 승인 결과와 disposition이 일치해야 합니다.");
            }
        }

        private static HandlingResult continueRun() {
            return new HandlingResult(Disposition.CONTINUE, null);
        }

        private static HandlingResult waitingApproval() {
            return new HandlingResult(Disposition.WAITING_APPROVAL, null);
        }

        private static HandlingResult waitingInput() {
            return new HandlingResult(Disposition.WAITING_INPUT, null);
        }

        private static HandlingResult autoApproval(AutoApprovalResume resume) {
            return new HandlingResult(Disposition.AUTO_APPROVAL, resume);
        }

        private static HandlingResult completed() {
            return new HandlingResult(Disposition.COMPLETED, null);
        }

        private static HandlingResult failed() {
            return new HandlingResult(Disposition.FAILED, null);
        }

        private static HandlingResult expired() {
            return new HandlingResult(Disposition.EXPIRED, null);
        }

        private static HandlingResult ignored() {
            return new HandlingResult(Disposition.IGNORED, null);
        }
    }

    /** Resume credentials are intentionally redacted from logs and debugger-friendly toString output. */
    public static final class AutoApprovalResume {
        private final Long approvalId;
        private final String approvalToken;
        private final String paramsCanonical;

        private AutoApprovalResume(Long approvalId, String approvalToken, String paramsCanonical) {
            this.approvalId = approvalId;
            this.approvalToken = approvalToken;
            this.paramsCanonical = paramsCanonical;
        }

        public Long approvalId() {
            return approvalId;
        }

        public String approvalToken() {
            return approvalToken;
        }

        public String paramsCanonical() {
            return paramsCanonical;
        }

        @Override
        public String toString() {
            return "AutoApprovalResume[approvalId=" + approvalId + ", credentials=[REDACTED]]";
        }
    }
}
