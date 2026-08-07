package HK.PrettyWorks_BE.agent.internal.preview;

import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.service.ApprovalPreviewRenderer;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.replan.constant.ReplanOperationType;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import HK.PrettyWorks_BE.replan.service.ReplanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 재계획 적용 승인 카드. 사용자가 실제 DB 변경을 허락하기 직전의 마지막 화면이다.
 *
 * <p><b>저장된 변경 목록을 직접 읽어 그린다.</b> 요청 본문에는 {@code scenarioType}만 있고,
 * 에이전트가 써 준 요약(summary)은 쓰지 않는다. 에이전트는 회의록·게시글 같은 외부 텍스트를 읽고 계획을 만드는데,
 * 그 텍스트에 지시문이 섞여 있으면 요약과 실제 변경이 다른 계획이 만들어질 수 있다.
 * 요약을 보여 주고 승인받으면 그 어긋남을 아무도 알아채지 못한다.
 *
 * <p>읽기는 {@code ReplanService.loadOperations}에 맡긴다. 적용 실행도 같은 메서드를 쓰므로
 * 보여준 것과 실행되는 것이 같은 경로에서 나오고, 소속 확인(남의 프로젝트 재계획인지)도 거기서 함께 이뤄진다.
 * 승인 카드는 실행 '전에' 그려지기 때문에, 여기서 걸러내지 않으면 실행이 막히더라도 내용은 이미 나간 뒤다.
 *
 * <p>건수를 종류별로 세어 보여 준다. 특히 할 일 삭제는 하드 삭제라 되돌릴 수 없으므로 따로 경고한다.
 */
@Component
@RequiredArgsConstructor
public class ReplanApplyPreviewRenderer implements ApprovalPreviewRenderer {

    private final ReplanService replanService;

    @Override
    public String tool() {
        return "replan.apply";
    }

    @Override
    public String render(JsonNode params) {
        JsonNode body = PreviewFields.object(params);
        long projectId = PreviewFields.requiredNumber(body, "projectId");
        long replanId = PreviewFields.requiredNumber(body, "replanId");
        ReplanScenarioType scenarioType = scenarioType(body);

        Map<ReplanOperationType, List<ReplanOperation>> byType =
                replanService.loadOperations(projectId, replanId, scenarioType);

        StringBuilder preview = new StringBuilder("재계획 #")
                .append(replanId)
                .append(" [")
                .append(scenarioType.getDescription())
                .append("] 을 적용합니다. (프로젝트 #")
                .append(projectId)
                .append(")");

        // 프로젝트 마감일은 팀 전체가 보는 값이라 날짜를 그대로 적는다.
        for (ReplanOperation operation : operationsOf(byType, ReplanOperationType.PROJECT_TARGET_DATE_CHANGE)) {
            preview.append("\n- 프로젝트 마감일: ")
                    .append(operation.from())
                    .append(" → ")
                    .append(operation.to());
        }

        appendCount(preview, byType, ReplanOperationType.PROJECT_MEMBER_ADD, "참여자 %d명을 추가합니다");
        appendCount(preview, byType, ReplanOperationType.MILESTONE_TARGET_DATE_CHANGE, "마일스톤 %d건의 목표일을 조정합니다");
        appendCount(preview, byType, ReplanOperationType.TASK_DUE_DATE_CHANGE, "할 일 %d건의 마감일을 조정합니다");
        appendCount(preview, byType, ReplanOperationType.TASK_CREATE, "할 일 %d건을 추가합니다");

        // 유일하게 되돌릴 수 없는 변경이다. 다른 줄과 같은 무게로 적으면 묻힌다.
        int deleted = operationsOf(byType, ReplanOperationType.TASK_DELETE).size();
        if (deleted > 0) {
            preview.append("\n- ⚠️ 할 일 ")
                    .append(deleted)
                    .append("건을 영구 삭제합니다 (되돌릴 수 없습니다)");
        }

        preview.append("\n※ 담당자에게 변경 알림이 발송됩니다.");
        return preview.toString();
    }

    // ================================= 내부 헬퍼 =================================

    private ReplanScenarioType scenarioType(JsonNode body) {
        String value = PreviewFields.requiredText(body, "scenarioType");
        try {
            return ReplanScenarioType.valueOf(value);
        } catch (IllegalArgumentException unknownType) {
            // 정의되지 않은 종류는 적용 단계에서 어차피 거절된다. 카드를 그리기 전에 끊는다.
            throw BaseException.type(AgentErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    private List<ReplanOperation> operationsOf(Map<ReplanOperationType, List<ReplanOperation>> byType,
                                               ReplanOperationType type) {
        return byType.getOrDefault(type, List.of());
    }

    private void appendCount(StringBuilder preview,
                             Map<ReplanOperationType, List<ReplanOperation>> byType,
                             ReplanOperationType type, String template) {
        int count = operationsOf(byType, type).size();
        if (count > 0) {
            preview.append("\n- ").append(String.format(template, count));
        }
    }
}
