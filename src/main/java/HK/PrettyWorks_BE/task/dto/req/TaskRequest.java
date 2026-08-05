package HK.PrettyWorks_BE.task.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

// 할 일 생성/수정 공용 요청 DTO (두 API의 body 구조가 동일).
@Builder
public record TaskRequest(
        @NotBlank(message = "할 일 이름을 입력해 주세요.")
        @Size(max = 100)
        String content,

        // 개인 할 일이면 null. 있으면 그 프로젝트의 할 일(작성자가 멤버여야 함).
        Long projectId,

        // 담당자. 비우면 본인이 담당한다.
        // 남을 지정하려면 그 프로젝트의 오너이거나 역할이 PM이어야 하고(TASK_008),
        // 대상도 참여중 멤버여야 한다(TASK_009). 개인 할 일에는 지정할 수 없다(TASK_010).
        // 수정 API에서는 무시한다 — 담당자 변경(재배정)은 지원하지 않는다.
        Long assigneeId,

        // 마감일(필수). D-day는 조회 시 파생 계산.
        @NotNull(message = "마감일을 입력해 주세요.")
        LocalDate dueDate
) {
}
