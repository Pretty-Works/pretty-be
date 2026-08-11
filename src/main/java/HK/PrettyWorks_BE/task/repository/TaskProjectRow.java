package HK.PrettyWorks_BE.task.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 프로젝트 인원 보드 조회 JPQL 프로젝션 결과 (task 필드 + 담당자 id/이름/부서 + 프로젝트명).
// done·dDay·overdue는 서비스에서 파생.
//
// projectName은 행마다 같은 값이다(조회 자체가 한 프로젝트로 한정). 그래도 조인으로 함께 읽는 것이
// 프로젝트를 따로 조회하는 것보다 싸다 — 이미 걸러진 행에 PK 조인 하나가 붙을 뿐이다.
public record TaskProjectRow(
        Long taskId,
        String content,
        LocalDateTime completedAt,
        LocalDate dueDate,
        Long assigneeId,
        String assigneeName,
        DepartmentType team,
        String projectName,
        // 권한 플래그(canEdit·canToggle·canDelete) 판정용.
        Long creatorId
) {
}
