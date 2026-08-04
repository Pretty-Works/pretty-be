package HK.PrettyWorks_BE.agent.dto.req;

import HK.PrettyWorks_BE.agent.constant.AgentActionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 승인 대기 중인 제안을 확정하는 요청 DTO. [승인] / [취소] 두 버튼이 같은 API를 쓴다.
//
// APPROVED는 화면에 반영할 값(action)을 응답으로 받아 가고, CANCELLED는 받을 게 없어 result가 null이다.
// 상태 전이라는 의미가 하나라 엔드포인트를 나누지 않았다. (기존 PATCH /tasks/{id}/status 와 같은 형태)
@Builder
public record AgentActionStatusRequest(

        // APPROVED 또는 CANCELLED만 유효하다. PENDING은 시작 상태라 전이 대상이 아니며,
        // 들어오면 AgentActionStatus.isResolution()에서 걸러 400으로 응답한다.
        @NotNull(message = "처리 결과를 선택해 주세요.")
        AgentActionStatus status
) {
}
