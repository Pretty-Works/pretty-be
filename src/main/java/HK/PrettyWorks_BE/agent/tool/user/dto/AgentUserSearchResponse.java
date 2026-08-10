package HK.PrettyWorks_BE.agent.tool.user.dto;

import HK.PrettyWorks_BE.agent.tool.security.AgentUser;
import lombok.Builder;

import java.util.List;

// user.search — 이름을 userId로 바꾸는 전사 검색.
//
// 개인정보라 최소 필드만 내려준다. 사번·이메일·전화번호·입사일은 넣지 않는다 —
// 에이전트가 답변에 적어 흘릴 경로 자체를 없앤다.
@Builder
public record AgentUserSearchResponse(
        List<AgentUser> users,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentUser(
            Long userId,
            String name,
            String department,
            String position,
            boolean isMe
    ) {
    }
}
