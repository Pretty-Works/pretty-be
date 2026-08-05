package HK.PrettyWorks_BE.project.post.dto.req;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @Schema(example = "프로젝트 일정 변경 안내", description = "게시글 제목 (200자 이하)")
        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 200, message = "게시글 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(example = "HIGH", description = "중요도 (HIGH / MID / LOW)")
        @NotNull(message = "게시글 중요도는 필수입니다.")
        PostPriority priority,

        @Schema(example = "프로젝트 일정이 변경되어 공유드립니다. 기존 7월 25일 예정이었던 API 개발 일정이 7월 30일로 변경되었습니다.",
                description = "게시글 내용")
        @NotBlank(message = "게시글 내용은 필수입니다.")
        @Size(max = 10_000, message = "게시글 내용은 10,000자 이하여야 합니다.")
        String content
) {
}
