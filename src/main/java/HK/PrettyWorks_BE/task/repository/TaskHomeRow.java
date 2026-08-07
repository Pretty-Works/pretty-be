package HK.PrettyWorks_BE.task.repository;

import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 홈 조회 JPQL 프로젝션 결과 (task 필드 + 프로젝트명). done·dDay는 서비스에서 파생.
public record TaskHomeRow(
        Long taskId,
        String content,
        LocalDateTime completedAt,
        LocalDate dueDate,
        Long projectId,
        String projectName,
        // 개인 할 일이면 null. 홈이 그룹 머리글에 상태 점으로 쓴다.
        ProjectStatus projectStatus,
        // 삭제 권한 판정용. 남이 배정한 할 일은 담당자라도 지울 수 없다.
        Long creatorId
) {
}
