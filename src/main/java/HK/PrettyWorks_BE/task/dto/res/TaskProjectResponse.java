package HK.PrettyWorks_BE.task.dto.res;

import HK.PrettyWorks_BE.user.constant.DepartmentType;

import java.time.LocalDate;
import java.util.List;

// 프로젝트 인원 보드 조회 응답. team은 enum 그대로 담아 코드명("PM")으로 직렬화되고,
// 정렬(내 팀 먼저 → 한글 가나다순)은 서비스에서 getDescription 기준으로 처리한다.
public record TaskProjectResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        Summary summary,
        List<TeamGroup> groups
) {
    // 이번 주 전체 집계 + 팀별 완료율
    public record Summary(
            int total,
            int done,
            int rate,               // 전체 완료율(내림), total=0이면 0
            List<TeamRate> teams
    ) {
    }

    // 팀별 완료율 (필드 순서는 명세: team, done, total, rate)
    public record TeamRate(
            DepartmentType team,
            int done,
            int total,
            int rate                // 팀 완료율(내림), total=0이면 0
    ) {
    }

    // 팀 그룹 (내 팀 먼저 → 나머지 한글 가나다순)
    public record TeamGroup(
            DepartmentType team,
            boolean isMine,         // 조회자 부서 == team
            List<TaskItem> tasks
    ) {
    }

    // 할 일 항목. done·dDay·overdue는 서비스에서 파생.
    public record TaskItem(
            Long taskId,
            String content,
            Assignee assignee,
            boolean done,           // completedAt != null
            LocalDate dueDate,
            Long dDay,              // 오늘~마감, 내림
            boolean overdue         // !done && dueDate < today
    ) {
    }

    // 담당자 요약
    public record Assignee(
            Long userId,
            String name
    ) {
    }
}
