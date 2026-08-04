package HK.PrettyWorks_BE.task.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

// 할 일 완료 토글 요청. done=true면 완료(completedAt 기록), false면 완료 취소.
@Builder
public record TaskStatusRequest(
        @NotNull(message = "완료 여부를 입력해 주세요.")
        Boolean done
) {
}
