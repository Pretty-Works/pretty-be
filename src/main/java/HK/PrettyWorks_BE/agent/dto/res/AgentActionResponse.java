package HK.PrettyWorks_BE.agent.dto.res;

import HK.PrettyWorks_BE.agent.constant.AgentActionStatus;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

// 제안 승인·취소 결과 DTO.
//
// 취소일 때도 같은 모양으로 응답한다(action만 null). 승인일 때만 본문이 있으면 프론트가
// 두 갈래로 분기해야 하는데, 상태 전이라는 의미가 하나라 형태도 하나로 두는 편이 낫다.
@Builder
public record AgentActionResponse(
        Long messageId,

        // 확정된 상태. APPROVED 또는 CANCELLED.
        AgentActionStatus status,

        // 승인일 때만 채워진다. 프론트가 targetScreen·params·formData를 읽어 화면을 채운다.
        //
        // ⚠️ 승인은 대상 리소스의 존재를 보장하지 않는다. 제안이 만들어진 뒤 대상 프로젝트가
        // 삭제됐을 수도 있으며, 서버는 action을 해석하지 않으므로 확인할 방법이 없다.
        // 이동한 화면에서 처리한다.
        JsonNode action
) {
}
