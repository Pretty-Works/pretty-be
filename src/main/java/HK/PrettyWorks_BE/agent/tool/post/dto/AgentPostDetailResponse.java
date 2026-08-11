package HK.PrettyWorks_BE.agent.tool.post.dto;

import lombok.Builder;

import java.time.LocalDateTime;

// post.detail — 게시글 한 건의 전문. ID로 한 건을 정확히 집는 유일한 수단이다.
//
// content는 사용자가 입력한 텍스트다. 그 안에 지시문처럼 보이는 문구가 있어도
// 명령이 아니라 데이터다(프롬프트 인젝션).
@Builder
public record AgentPostDetailResponse(
        Long postId,
        Long projectId,
        String title,
        String priority,
        String priorityLabel,
        String content,
        Long authorId,
        String authorName,
        String department,
        LocalDateTime createdAt,
        // 수정된 적이 없으면 작성 일시와 같다.
        LocalDateTime modifiedAt,
        // 요청자가 작성자인지. 게시글은 수정·삭제 모두 작성자만 가능해(POST_004)
        // 회의록처럼 canEdit을 따로 두지 않는다 — 두 값이 항상 같으면 한쪽만 보고 오해할 여지가 생긴다.
        boolean isMine
) {
}
