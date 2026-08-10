package HK.PrettyWorks_BE.agent.interaction.preview;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.replan.constant.ReplanPolicy;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.constant.RiskLevel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 재계획 저장 승인 카드.
//
// 이 단계에서는 프로젝트 데이터가 바뀌지 않는다. 그 사실을 카드에 못 박아 둔다 —
// 승인 버튼이 늘 "데이터를 바꾼다"는 뜻이었으므로, 아무 말 없으면 사용자는 여기서도 그렇게 읽는다.
@Component
public class ReplanCreatePreviewRenderer implements ApprovalPreviewRenderer {

    private static final int MAX_PREVIEW_SUMMARY = 80;

    @Override
    public String tool() {
        return "replan.create";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        long projectId = PreviewFields.requiredNumber(body, "projectId");

        JsonNode scenarios = body.get("scenarios");
        if (scenarios == null || !scenarios.isArray() || scenarios.isEmpty()
                || scenarios.size() > ReplanPolicy.MAX_SCENARIOS) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }

        StringBuilder preview = new StringBuilder("재계획안 ")
                .append(scenarios.size())
                .append("개를 저장합니다. (프로젝트 #")
                .append(projectId)
                .append(")");

        for (JsonNode scenario : scenarios) {
            if (scenario == null || !scenario.isObject()) {
                throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
            }
            preview.append("\n- [")
                    .append(label(scenario))
                    .append(" · 위험 ")
                    .append(risk(scenario))
                    .append("] ")
                    .append(shorten(PreviewFields.requiredText(scenario, "summary")))
                    .append(" — 변경 ")
                    .append(PreviewFields.arraySize(scenario, "operations"))
                    .append("건");
        }

        // 실제 변경은 적용 승인에서 다시 확인한다. 여기서 승인해도 아직 아무것도 바뀌지 않는다.
        return preview.append("\n※ 선택지를 저장할 뿐, 프로젝트 데이터는 아직 바뀌지 않습니다.").toString();
    }

    private String label(JsonNode scenario) {
        String value = PreviewFields.requiredText(scenario, "scenarioType");
        try {
            return ReplanScenarioType.valueOf(value).getDescription();
        } catch (IllegalArgumentException unknownType) {
            // 정의되지 않은 종류는 저장 단계에서 어차피 거절된다. 카드를 그리기 전에 끊는다.
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    private String risk(JsonNode scenario) {
        String value = PreviewFields.requiredText(scenario, "risk");
        try {
            return RiskLevel.valueOf(value).getDescription();
        } catch (IllegalArgumentException unknownRisk) {
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    private String shorten(String summary) {
        if (summary.length() <= MAX_PREVIEW_SUMMARY) {
            return summary;
        }
        return summary.substring(0, MAX_PREVIEW_SUMMARY - 1) + "…";
    }
}
