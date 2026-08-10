package HK.PrettyWorks_BE.agent.tool.replan.dto;

import HK.PrettyWorks_BE.replan.constant.ReplanPolicy;
import HK.PrettyWorks_BE.replan.dto.req.ReplanCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

// replan.create 요청.
//
// projectId를 본문에도 담는 이유: 승인 토큰은 요청 본문의 해시로 봉인되므로, 경로 변수만으로 대상을 정하면
// 승인 때와 다른 프로젝트로 보내도 해시가 그대로다. AgentWriteExecutor가 본문 값과 경로 변수를 대조한다.
@Builder
public record AgentReplanCreateRequest(

        @NotNull(message = "프로젝트를 선택해 주세요.")
        @Schema(description = "대상 프로젝트 ID. 경로 변수와 같아야 한다", example = "1")
        Long projectId,

        @Size(max = 500, message = "재계획 사유는 최대 500자까지 입력 가능합니다.")
        @Schema(description = "재계획이 필요해진 이유", example = "핵심 개발자 휴가로 일정 지연 위험")
        String reason,

        @Valid
        @NotEmpty(message = "시나리오를 하나 이상 입력해 주세요.")
        @Size(max = ReplanPolicy.MAX_SCENARIOS, message = "시나리오는 최대 5개까지 제시할 수 있습니다.")
        @Schema(description = "사용자가 고를 선택지")
        List<ReplanCreateRequest.ScenarioRequest> scenarios
) {

    public ReplanCreateRequest toDomain() {
        return ReplanCreateRequest.builder()
                .reason(reason)
                .scenarios(scenarios)
                .build();
    }
}
