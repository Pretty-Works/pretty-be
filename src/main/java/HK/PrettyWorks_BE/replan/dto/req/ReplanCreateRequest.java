package HK.PrettyWorks_BE.replan.dto.req;

import HK.PrettyWorks_BE.replan.constant.ReplanPolicy;
import HK.PrettyWorks_BE.replan.constant.ReplanScenarioType;
import HK.PrettyWorks_BE.replan.constant.RiskLevel;
import HK.PrettyWorks_BE.replan.dto.ReplanOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

// 재계획 저장 요청. 이 단계에서는 프로젝트 데이터가 전혀 바뀌지 않는다 —
// 선택지를 만들어 두는 것뿐이고, 실제 변경은 적용 API가 한다.
@Builder
public record ReplanCreateRequest(

        @Size(max = 500, message = "재계획 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "재계획이 필요해진 이유", example = "핵심 개발자 휴가로 일정 지연 위험")
        String reason,

        @Valid
        @NotEmpty(message = "시나리오를 하나 이상 입력해 주세요.")
        @Size(max = ReplanPolicy.MAX_SCENARIOS, message = "시나리오는 최대 5개까지 제시할 수 있습니다.")
        List<ScenarioRequest> scenarios
) {

    @Builder
    public record ScenarioRequest(

            @NotNull(message = "시나리오 종류를 입력해 주세요.")
            @Schema(description = "해결 전략 분류", example = "EXTEND")
            ReplanScenarioType scenarioType,

            @NotBlank(message = "시나리오 요약을 입력해 주세요.")
            @Size(max = 500, message = "시나리오 요약은 최대 500자까지 입력 가능합니다.")
            @Schema(description = "사용자에게 보여줄 한 줄 설명", example = "프로젝트 마감일을 3일 연장합니다")
            String summary,

            @NotNull(message = "위험도를 입력해 주세요.")
            @Schema(description = "선택을 돕는 위험도 라벨", example = "LOW")
            RiskLevel risk,

            @Valid
            @NotEmpty(message = "변경 항목을 하나 이상 입력해 주세요.")
            @Size(max = ReplanPolicy.MAX_OPERATIONS, message = "변경 항목은 최대 50건까지 담을 수 있습니다.")
            @Schema(description = "실제 변경 목록. 적용 순서는 서버가 정하므로 배열 순서는 의미가 없다")
            List<ReplanOperation> operations
    ) {
    }
}
