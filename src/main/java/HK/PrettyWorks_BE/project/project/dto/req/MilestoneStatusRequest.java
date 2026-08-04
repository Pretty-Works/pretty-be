package HK.PrettyWorks_BE.project.project.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 마일스톤 완료 토글 요청. true면 완료(completed_at 기록), false면 완료 취소.
@Builder
public record MilestoneStatusRequest(
        @NotNull(message = "완료 여부를 입력해 주세요.")
        Boolean done
) {
}
