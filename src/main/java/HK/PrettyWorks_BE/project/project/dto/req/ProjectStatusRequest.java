package HK.PrettyWorks_BE.project.project.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

// 프로젝트 상태 변경 요청. 유효한 ProjectStatus 값인지는 서비스에서 검증(PROJECT_018).
@Builder
public record ProjectStatusRequest(
        @NotBlank(message = "상태를 입력해주세요.")
        String status
) {
}
