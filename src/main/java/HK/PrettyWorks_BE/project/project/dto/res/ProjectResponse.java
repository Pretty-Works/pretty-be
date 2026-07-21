package HK.PrettyWorks_BE.project.project.dto.res;

import lombok.Builder;

// 프로젝트 생성/수정 공용 응답 DTO.
@Builder
public record ProjectResponse(
        Long projectId
) {
}
