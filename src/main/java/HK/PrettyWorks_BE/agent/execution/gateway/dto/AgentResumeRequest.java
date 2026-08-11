package HK.PrettyWorks_BE.agent.execution.gateway.dto;

import HK.PrettyWorks_BE.agent.interaction.domain.AgentDecision;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// FastAPI /api/agent/runs/{runId}/resume 의 본문. 필드 이름은 그쪽 ResumeRequest 와
// 1:1로 맞춰야 한다 — pydantic 은 모르는 필드를 조용히 버리므로(extra=ignore),
// 이름이 어긋나면 422 대신 "selectedIds and text both empty" 400 이 돌아오고
// BE 는 그걸 AGENT_007 로 번역해 실행이 통째로 죽는다.
//
// 승인 재개: toolCallId·decision (+ APPROVED 면 approvalToken·paramsCanonical)
// 질문 재개: questionId·selectedIds·text
// kind·interactionId 는 FastAPI 가 쓰지 않는 로그 상관용 필드다(무시됨).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResumeRequest(
        String kind,
        Long interactionId,
        String toolCallId,
        AgentDecision decision,
        String alternativeId,
        String reason,
        String approvalToken,
        String paramsCanonical,
        Long questionId,
        List<String> selectedIds,
        String text,

        // 정보용. FastAPI 는 이 값으로 동작을 바꾸지 않는다(규격 §5-1). 매 재개마다 현재값을 보낸다.
        boolean autoApprove
) {
    private static final String APPROVAL = "APPROVAL";
    private static final String QUESTION = "QUESTION";

    public AgentResumeRequest {
        Objects.requireNonNull(interactionId, "interactionId");
        selectedIds = selectedIds == null ? null : List.copyOf(selectedIds);
    }

    public static AgentResumeRequest approval(Long approvalId, String toolCallId,
                                              AgentDecision decision,
                                              String alternativeId, String reason,
                                              String approvalToken,
                                              String paramsCanonical,
                                              boolean autoApprove) {
        Objects.requireNonNull(decision, "decision");
        boolean approved = decision == AgentDecision.APPROVED;
        boolean hasToken = approvalToken != null && !approvalToken.isBlank();
        boolean hasParams = paramsCanonical != null && !paramsCanonical.isBlank();
        if ((approved && (!hasToken || !hasParams))
                || (!approved && (approvalToken != null || paramsCanonical != null))) {
            throw new IllegalArgumentException(
                    "APPROVED resume requires approvalToken and paramsCanonical");
        }
        return new AgentResumeRequest(APPROVAL, approvalId, toolCallId, decision,
                alternativeId, reason, approvalToken, paramsCanonical,
                null, null, null, autoApprove);
    }

    // 저장해 둔 응답(JSON)을 FastAPI 가 아는 두 필드로 풀어서 보낸다.
    // 저장 형식(selectedOptionIds·freeText)은 FE·DB 계약이라 그대로 두고 여기서만 변환한다.
    public static AgentResumeRequest question(Long questionId, JsonNode response,
                                              boolean autoApprove) {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(response, "response");
        List<String> selectedIds = new ArrayList<>();
        for (JsonNode optionId : response.path("selectedOptionIds")) {
            if (optionId.isTextual()) {
                selectedIds.add(optionId.textValue());
            }
        }
        JsonNode freeText = response.path("freeText");
        return new AgentResumeRequest(QUESTION, questionId, null, null, null, null, null, null,
                questionId, List.copyOf(selectedIds),
                freeText.isTextual() ? freeText.textValue() : null, autoApprove);
    }

    @Override
    public String toString() {
        return "AgentResumeRequest[kind=" + kind + ", interactionId=" + interactionId
                + ", toolCallId=" + toolCallId + ", decision=" + decision
                + ", alternativeId=" + alternativeId
                + ", selectedIds=" + selectedIds
                + ", credentials=" + (approvalToken == null ? "none" : "[REDACTED]") + "]";
    }
}
