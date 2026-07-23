package HK.PrettyWorks_BE.project.project.dto.res;

import lombok.Builder;

@Builder
public record ProjectCreateResponse(
        Long projectId
) {
}
