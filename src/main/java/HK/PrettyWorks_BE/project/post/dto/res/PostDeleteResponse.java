package HK.PrettyWorks_BE.project.post.dto.res;

import lombok.Builder;

@Builder
public record PostDeleteResponse(
        Long postId
) {
}