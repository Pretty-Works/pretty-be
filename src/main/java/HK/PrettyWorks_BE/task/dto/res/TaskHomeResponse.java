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

    // 홈은 내가 담당한 할 일만 나오므로 완료 토글과 수정은 항상 가능하다.
    // 삭제만 작성자 전용이라, 남이 배정한 할 일에서는 닫아야 해서 플래그로 내려준다.
    public record TaskItem(
            Long taskId,
            String content,
            boolean done,         // completedAt != null 에서 파생
            LocalDate dueDate,
            Long dDay,            // DAYS.between(today, dueDate) 에서 파생
            boolean canDelete     // 작성자만. 남이 배정한 할 일이면 false
    ) {
    }
}
