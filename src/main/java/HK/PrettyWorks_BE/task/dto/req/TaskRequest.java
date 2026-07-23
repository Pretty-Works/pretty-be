package HK.PrettyWorks_BE.task.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

// 할 일 생성/수정 공용 요청 DTO (두 API의 body 구조가 동일).
@Builder
public record TaskRequest(
        @NotBlank(message = "할 일 이름을 입력해 주세요.")
        String content,

        // 개인 할 일이면 null. 있으면 그 프로젝트의 할 일(작성자가 멤버여야 함).
        Long projectId,

        // 마감일(필수). D-day는 조회 시 파생 계산.
        @NotNull(message = "마감일을 입력해 주세요.")
        LocalDate dueDate
) {
}
