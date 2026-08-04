package HK.PrettyWorks_BE.task.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 홈 조회 JPQL 프로젝션 결과 (task 필드 + 프로젝트명). done·dDay는 서비스에서 파생.
public record TaskHomeRow(
        Long taskId,
        String content,
        LocalDateTime completedAt,
        LocalDate dueDate,
        Long projectId,
        String projectName
) {
}
