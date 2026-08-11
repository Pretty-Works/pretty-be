package HK.PrettyWorks_BE.agent.interaction.application;

import HK.PrettyWorks_BE.agent.conversation.domain.AgentConversationEntity;
import HK.PrettyWorks_BE.agent.conversation.persistence.AgentConversationRepository;
import HK.PrettyWorks_BE.agent.execution.application.AgentRunStateMachine;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.execution.domain.AgentRunStatus;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionEntity;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionKind;
import HK.PrettyWorks_BE.agent.interaction.domain.AgentInteractionStatus;
import HK.PrettyWorks_BE.agent.interaction.persistence.AgentInteractionRepository;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.shared.limit.AgentPayloadLimits;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentInteractionResolutionService {
    private static final String ALWAYS_ID = "ALWAYS";

    private final AgentRunRepository runRepository;
    private final AgentInteractionRepository interactionRepository;
    private final AgentConversationRepository conversationRepository;
    private final AgentInteractionService interactionService;
    private final AgentRunStateMachine stateMachine;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    public AgentInteractionResolutionService(
            AgentRunRepository runRepository,
            AgentInteractionRepository interactionRepository,
            AgentConversationRepository conversationRepository,
            AgentInteractionService interactionService,
            AgentRunStateMachine stateMachine,
            ObjectMapper objectMapper,
            CurrentUserService currentUserService
    ) {
        this.runRepository = runRepository;
        this.interactionRepository = interactionRepository;
        this.conversationRepository = conversationRepository;
        this.interactionService = interactionService;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public PreparedApproval resolveApproval(Long userId, Long approvalId,
                                            AgentApprovalRequest request) {
        currentUserService.getEmployedUser(userId);
        LockedInteraction locked = lockOwnedInteraction(userId, approvalId,
                AgentInteractionKind.APPROVAL, AgentRunStatus.WAITING_APPROVAL);
        String alternativeId = normalizeOptional(request.alternativeId());
        String reason = normalizeOptional(request.reason());
        boolean always = request.decision() == AgentDecision.ALTERNATIVE
                && ALWAYS_ID.equals(alternativeId);
        AgentDecision persistedDecision = always ? AgentDecision.APPROVED : request.decision();
        AgentInteractionStatus persistedStatus = AgentInteractionStatus.valueOf(
                persistedDecision.name());
        String persistedAlternativeId = always ? ALWAYS_ID : alternativeId;

        interactionService.resolve(approvalId, new AgentInteractionService.Resolution(
                persistedStatus, persistedDecision, persistedAlternativeId,
                null, reason));
        transitionToRunning(locked.run(), AgentRunStatus.WAITING_APPROVAL);

        if (always) {
            AgentConversationEntity conversation = conversationRepository
                    .findByIdForUpdate(locked.run().getConversationId())
                    .orElseThrow(() -> BaseException.type(AgentErrorCode.CONVERSATION_NOT_FOUND));
            conversation.changeAutoApprove(true);
        }

        boolean tokenRequired = persistedDecision == AgentDecision.APPROVED;
        return new PreparedApproval(locked.run().getId(), locked.run().getRunId(), approvalId,
                persistedDecision, always ? null : alternativeId, reason,
                tokenRequired, tokenRequired ? locked.interaction().getParamsCanonical() : null,
                locked.run().getLastEventSeq());
    }

    @Transactional
    public PreparedQuestion resolveQuestion(Long userId, Long questionId,
                                            AgentQuestionAnswerRequest request) {
        currentUserService.getEmployedUser(userId);
        LockedInteraction locked = lockOwnedInteraction(userId, questionId,
                AgentInteractionKind.QUESTION, AgentRunStatus.WAITING_INPUT);
        ObjectNode response = validateQuestionResponse(locked.interaction(), request);
        interactionService.resolve(questionId, new AgentInteractionService.Resolution(
                AgentInteractionStatus.ANSWERED, null, null,
                objectMapper.writeValueAsString(response), null));
        transitionToRunning(locked.run(), AgentRunStatus.WAITING_INPUT);
        return new PreparedQuestion(locked.run().getId(), locked.run().getRunId(),
                questionId, response, locked.run().getLastEventSeq());
    }

    private LockedInteraction lockOwnedInteraction(Long userId, Long interactionId,
                                                    AgentInteractionKind expectedKind,
                                                    AgentRunStatus expectedRunStatus) {
        AgentInteractionEntity snapshot = interactionRepository.findById(interactionId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND));
        AgentRunEntity run = runRepository.findByIdForUpdate(snapshot.getRunId())
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        if (!run.isOwnedBy(userId)) {
            throw BaseException.type(AgentErrorCode.NOT_MY_RUN);
        }
        AgentInteractionEntity interaction = interactionRepository.findByIdForUpdate(interactionId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND));
        if (!interaction.getRunId().equals(run.getId())
                || interaction.getKind() != expectedKind) {
            throw BaseException.type(AgentErrorCode.INTERACTION_NOT_FOUND);
        }
        if (run.getStatus() != expectedRunStatus) {
            throw BaseException.type(AgentErrorCode.INTERACTION_ALREADY_RESOLVED);
        }
        return new LockedInteraction(run, interaction);
    }

    private void transitionToRunning(AgentRunEntity run, AgentRunStatus expected) {
        if (!stateMachine.transitionLocked(run, EnumSet.of(expected),
                AgentRunStatus.RUNNING, null)) {
            throw BaseException.type(AgentErrorCode.INTERACTION_ALREADY_RESOLVED);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ObjectNode validateQuestionResponse(AgentInteractionEntity interaction,
                                                AgentQuestionAnswerRequest request) {
        try {
            JsonNode payload = objectMapper.readTree(interaction.getPayloadJson());
            List<String> selectedIds = request.selectedOptionIds();
            String freeText = request.freeText() == null ? null : request.freeText().trim();
            boolean hasFreeText = freeText != null && !freeText.isBlank();
            if (selectedIds.isEmpty() && !hasFreeText) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }

            Set<String> distinctIds = new HashSet<>(selectedIds);
            if (distinctIds.size() != selectedIds.size()) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
            boolean multiple = payload.path("multiple").asBoolean(false);
            if (!multiple && selectedIds.size() > 1) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
            Set<String> allowedIds = new HashSet<>();
            JsonNode options = payload.get("options");
            if (options != null && options.isArray()) {
                for (JsonNode option : options) {
                    JsonNode id = option.get("id");
                    if (id != null && id.isTextual()) {
                        allowedIds.add(id.textValue());
                    }
                }
            }
            if (!allowedIds.containsAll(selectedIds)
                    || (hasFreeText && !payload.path("allowFreeText").asBoolean(true))) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }

            ObjectNode response = objectMapper.createObjectNode();
            var selected = response.putArray("selectedOptionIds");
            selectedIds.forEach(selected::add);
            if (hasFreeText) {
                response.put("freeText", freeText);
            }
            if (objectMapper.writeValueAsBytes(response).length
                    > AgentPayloadLimits.MAX_INTERACTION_RESPONSE_BYTES) {
                throw BaseException.type(GlobalErrorCode.VALIDATION_ERROR);
            }
            return response;
        } catch (BaseException expected) {
            throw expected;
        } catch (RuntimeException invalidStoredPayload) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    private record LockedInteraction(AgentRunEntity run, AgentInteractionEntity interaction) {
    }

    public record PreparedApproval(Long internalRunId, String publicRunId, Long approvalId,
                                   AgentDecision decision, String alternativeId, String reason,
                                   boolean tokenRequired, String paramsCanonical,
                                   long lastEventSequence) {
    }

    public record PreparedQuestion(Long internalRunId, String publicRunId, Long questionId,
                                   JsonNode response, long lastEventSequence) {
        public PreparedQuestion {
            response = response.deepCopy();
        }

        @Override
        public JsonNode response() {
            return response.deepCopy();
        }
    }
}
