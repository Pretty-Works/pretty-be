package HK.PrettyWorks_BE.task.dto.res;

import java.time.LocalDate;
import java.util.List;

// 홈 조회 응답: 프로젝트별 그룹 목록. 개인 할 일 그룹(projectId=null)은 마지막.
public record TaskHomeResponse(
        List<TaskGroup> groups
) {

    public record TaskGroup(
            Long projectId,       // 개인 할 일 그룹이면 null
            String projectName,   // 개인 할 일 그룹이면 null
            List<TaskItem> tasks
    ) {
    }

    public record TaskItem(
            Long taskId,
            String content,
            boolean done,         // completedAt != null 에서 파생
            LocalDate dueDate,
            Long dDay             // DAYS.between(today, dueDate) 에서 파생
    ) {
    }
}
