package HK.PrettyWorks_BE.project.project.dto.res;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import lombok.Builder;

// 프로젝트 상태 변경 응답. status는 Jackson이 enum 이름("HOLDING" 등)으로 직렬화한다.
@Builder
public record ProjectStatusResponse(
        Long projectId,
        ProjectStatus status
) {
}
