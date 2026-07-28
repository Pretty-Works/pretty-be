package HK.PrettyWorks_BE.project.post.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @Schema(example = "게시글 제목1", description = "게시글 제목")
        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 200, message = "게시글 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(example = "게시글 내용...", description = "게시글 내용")
        @NotBlank(message = "게시글 내용은 필수입니다.")
        String content
) {
}
