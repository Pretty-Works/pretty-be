package HK.PrettyWorks_BE.agent.tool.post.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

// post.list — "게시판에 어떤 글이 올라와 있나"만 답한다.
//
// 본문(content)은 내려가지 않는다. 게시글 한 건이 최대 10,000자라 여러 건을 통째로 받으면
// 컨텍스트가 터지므로, 한 건의 전문이 필요하면 여기서 postId를 찾아 post.detail을 부른다.
@Builder
public record AgentPostListResponse(
        Long projectId,
        List<AgentPost> posts,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentPost(
            Long postId,
            String title,
            // HIGH / MID / LOW.
            String priority,
            // 높음 / 중간 / 낮음. 에이전트가 한국어 답변에서 코드값을 직접 번역하지 않게 함께 준다.
            String priorityLabel,
            String authorName,
            String department,
            LocalDateTime createdAt
    ) {
    }
}
