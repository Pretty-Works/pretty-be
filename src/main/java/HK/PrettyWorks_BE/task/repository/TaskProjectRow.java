package HK.PrettyWorks_BE.task.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 프로젝트 인원 보드 조회 JPQL 프로젝션 결과 (task 필드 + 담당자 id/이름/부서).
// done·dDay·overdue는 서비스에서 파생.
public record TaskProjectRow(
        Long taskId,
        String content,
        LocalDateTime completedAt,
        LocalDate dueDate,
        Long assigneeId,
        String assigneeName,
        DepartmentType team
) {
}
