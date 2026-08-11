package HK.PrettyWorks_BE.agent.tool.project.dto;

import lombok.Builder;

import java.util.List;

// project.members — 이름을 userId로 바꾸는 프로젝트 범위 조회.
//
// 회의록 attendeeIds를 채우는 유일한 경로다. 동명이인을 임의로 고르지 않도록 부서·직책을 함께 준다.
@Builder
public record AgentMemberListResponse(
        Long projectId,
        List<AgentMember> members,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentMember(
            Long userId,
            String name,
            String department,
            String position,
            String role,
            boolean isOwner,
            // 회의록은 작성자를 참석자에 넣을 수 없다(MEETING_006). 에이전트가 본인을 빼고
            // 명단을 만들 수 있어야 해서 내려준다.
            boolean isMe
    ) {
    }
}
