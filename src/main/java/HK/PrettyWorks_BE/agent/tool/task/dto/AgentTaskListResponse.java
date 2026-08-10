package HK.PrettyWorks_BE.agent.tool.task.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// task.list — 할 일 주간 조회. projectId 유무로 "내 할일"과 "프로젝트 전원"이 갈린다.
//
// 완료율은 서버가 센다. 에이전트가 직접 세면 carry-over 포함 기준이 달라져 답변의 숫자가 어긋난다.
@Builder
public record AgentTaskListResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        // MINE 또는 PROJECT. 어느 모드로 동작했는지 에이전트가 알아야 답변의 주어가 정해진다.
        String scope,
        List<AgentTask> tasks,
        Summary summary,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentTask(
            Long taskId,
            String content,
            LocalDate dueDate,
            boolean completed,
            // 지난 주 이전 마감인데 아직 미완료라 이번 주로 밀려온 건.
            boolean isCarryOver,
            // 내용·마감일을 고칠 수 있는지. 담당자 또는 작성자면 true (TASK_004).
            //
            // canToggle과 나눠 둔 이유: 두 권한이 다르다. 배정한 PM은 오타를 고칠 수 있지만
            // 완료 체크는 못 하고(완료율이 실제 진척과 어긋나므로), 배정받은 사람은 둘 다 된다.
            // 한 필드로 합치면 둘 중 하나는 반드시 틀린 답이 된다.
            boolean canEdit,
            // 완료·미완료를 바꿀 수 있는지. 담당자만 true. task.toggleStatus 가능 여부의 기준.
            boolean canToggle,
            Long assigneeId,
            String assigneeName,
            Long projectId,
            String projectName
    ) {
    }

    @Builder
    public record Summary(
            int total,
            int completed,
            int completionRate,
            int carryOverCount
    ) {
    }
}
