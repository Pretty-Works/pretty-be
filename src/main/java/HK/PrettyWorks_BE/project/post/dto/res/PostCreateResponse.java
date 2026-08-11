package HK.PrettyWorks_BE.project.post.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record PostCreateResponse(
        @Schema(example = "8", description = "생성된 게시글 id")
        Long postId
) {
}
