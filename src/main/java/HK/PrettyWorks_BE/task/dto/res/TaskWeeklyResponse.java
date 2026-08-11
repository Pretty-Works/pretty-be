package HK.PrettyWorks_BE.task.dto.res;

import HK.PrettyWorks_BE.task.repository.TaskHomeRow;
import lombok.Builder;

import java.time.LocalDate;

// 본인 담당 할 일 한 건(주간 조회). 담당자는 조회한 본인으로 고정이라 담지 않는다.
//
// 리포지토리 프로젝션(TaskHomeRow)을 서비스 밖으로 내보내지 않기 위한 경계다.
// 쿼리 프로젝션이 서비스 시그니처에 그대로 있으면 쿼리를 손볼 때 호출자가 함께 깨진다.
@Builder
public record TaskWeeklyResponse(
        Long taskId,
        String content,
        LocalDate dueDate,
        boolean completed,
        // 개인 할 일이면 둘 다 null.
        Long projectId,
        String projectName
) {
    public static TaskWeeklyResponse from(TaskHomeRow row) {
        return TaskWeeklyResponse.builder()
                .taskId(row.taskId())
                .content(row.content())
                .dueDate(row.dueDate())
                .completed(row.completedAt() != null)
                .projectId(row.projectId())
                .projectName(row.projectName())
                .build();
    }
}
