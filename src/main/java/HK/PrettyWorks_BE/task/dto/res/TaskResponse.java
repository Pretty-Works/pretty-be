package HK.PrettyWorks_BE.task.dto.res;

import lombok.Builder;

// 할 일 생성/수정 공용 응답 DTO.
@Builder
public record TaskResponse(
        Long taskId
) {
}
