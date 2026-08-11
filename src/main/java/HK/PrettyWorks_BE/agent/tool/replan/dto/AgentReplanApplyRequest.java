package HK.PrettyWorks_BE.agent.tool.replan.dto;

import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// replan.apply 요청.
//
// 변경 내용은 담지 않는다. 무엇을 바꿀지는 저장된 계획에서 읽으며, 요청은 "어느 재계획의 어느 시나리오냐"만 가리킨다.
// 변경 내용을 다시 실어 보내는 방식이면, 승인 화면에 보여 준 계획과 다른 것을 실행할 길이 열린다.
@Builder
public record AgentReplanApplyRequest(

        @NotNull(message = "프로젝트를 선택해 주세요.")
        @Schema(description = "대상 프로젝트 ID. 경로 변수와 같아야 한다", example = "1")
        Long projectId,

        @NotNull(message = "재계획을 선택해 주세요.")
        @Schema(description = "적용할 재계획 ID. 경로 변수와 같아야 한다", example = "123")
        Long replanId,

        @NotNull(message = "시나리오를 선택해 주세요.")
        @Schema(description = "적용할 시나리오 종류", example = "EXTEND")
        ReplanScenarioType scenarioType
) {
}
