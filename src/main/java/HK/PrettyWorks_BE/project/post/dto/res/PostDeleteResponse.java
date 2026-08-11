package HK.PrettyWorks_BE.project.post.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record PostDeleteResponse(
        @Schema(example = "1", description = "삭제된 게시글 id")
        Long postId
) {
}
