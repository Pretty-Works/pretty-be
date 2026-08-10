package HK.PrettyWorks_BE.agent.execution.gateway.dto;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResumeRequest(
        String kind,
        Long interactionId,
        AgentDecision decision,
        String alternativeId,
        String reason,
        JsonNode response,
        String approvalToken,
        String paramsCanonical
) {
    private static final String APPROVAL = "APPROVAL";
    private static final String QUESTION = "QUESTION";

    public AgentResumeRequest {
        Objects.requireNonNull(interactionId, "interactionId");
        response = response == null ? null : response.deepCopy();
    }

    public static AgentResumeRequest approval(Long approvalId, AgentDecision decision,
                                              String alternativeId, String reason,
                                              String approvalToken,
                                              String paramsCanonical) {
        Objects.requireNonNull(decision, "decision");
        boolean approved = decision == AgentDecision.APPROVED;
        boolean hasToken = approvalToken != null && !approvalToken.isBlank();
        boolean hasParams = paramsCanonical != null && !paramsCanonical.isBlank();
        if ((approved && (!hasToken || !hasParams))
                || (!approved && (approvalToken != null || paramsCanonical != null))) {
            throw new IllegalArgumentException(
                    "APPROVED resume requires approvalToken and paramsCanonical");
        }
        return new AgentResumeRequest(APPROVAL, approvalId, decision, alternativeId, reason,
                null, approvalToken, paramsCanonical);
    }

    public static AgentResumeRequest question(Long questionId, JsonNode response) {
        Objects.requireNonNull(response, "response");
        return new AgentResumeRequest(QUESTION, questionId, null, null, null,
                response, null, null);
    }

    @Override
    public JsonNode response() {
        return response == null ? null : response.deepCopy();
    }

    @Override
    public String toString() {
        return "AgentResumeRequest[kind=" + kind + ", interactionId=" + interactionId
                + ", decision=" + decision + ", alternativeId=" + alternativeId
                + ", credentials=" + (approvalToken == null ? "none" : "[REDACTED]") + "]";
    }
}
