package HK.PrettyWorks_BE.project.post.dto.req;

import HK.PrettyWorks_BE.project.post.constant.PostPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @Schema(example = "프로젝트 일정 변경 안내 (2차 수정)", description = "게시글 제목 (200자 이하)")
        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 200, message = "게시글 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(example = "MID", description = "중요도 (HIGH / MID / LOW)")
        @NotNull(message = "게시글 중요도는 필수입니다.")
        PostPriority priority,

        @Schema(example = "일정이 한 번 더 조정되어 재공유드립니다. API 개발 일정은 8월 1일로 확정되었습니다.",
                description = "게시글 내용")
        @NotBlank(message = "게시글 내용은 필수입니다.")
        String content
) {
}
