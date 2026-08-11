package HK.PrettyWorks_BE.agent.tool.post.dto;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import HK.PrettyWorks_BE.project.post.dto.req.PostCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// post.create 요청. 공개 API 바디(3필드)에 projectId를 더한 형태다.
// 경로 변수를 바디에 싣는 이유는 AgentMeetingCreateRequest 주석 참고.
//
// 길이 제약을 여기에 다시 적는 이유: 공개 API는 컨트롤러의 @Valid가 PostCreateRequest를 검증하지만
// 내부 도구는 그 경로를 타지 않는다(AgentWriteExecutor가 이 DTO만 검증한다). 제약이 없으면
// LLM이 만든 250자 제목이 그대로 내려가 title 컬럼(200) 저장에서 500으로 터진다.
// 숫자의 출처는 PostCreateRequest다 — 그쪽이 바뀌면 여기도 함께 고친다.
public record AgentPostCreateRequest(
        @NotNull Long projectId,
        @NotBlank @Size(max = 200) String title,
        @NotNull PostPriority priority,
        @NotBlank @Size(max = 10_000) String content
) {
    public PostCreateRequest toDomain() {
        return new PostCreateRequest(title, priority, content);
    }
}
