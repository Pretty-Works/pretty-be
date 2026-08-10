package HK.PrettyWorks_BE.agent.summary.dto;

import HK.PrettyWorks_BE.agent.tool.post.dto.AgentPostListResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentBudgetSummaryResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentExpenseListResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentMilestoneListResponse;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskListResponse;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FastAPI {@code POST /api/agent/project-summary} 요청 바디.
 *
 * <p>Run이 아니라 단발 호출이라 {@code X-Run-Id}가 없고, 따라서 FastAPI가 내부 도구를 되불러
 * 재료를 모을 수단이 없습니다. 그래서 요약에 필요한 재료를 여기에 전부 실어 보냅니다
 * (회의록 초안 생성과 같은 방식).</p>
 *
 * <p>필드 타입을 새로 정의하지 않고 내부 도구 응답(milestone.list · task.list · budget.summary ·
 * expense.list · post.list)을 그대로 재사용합니다. 요약 전용 DTO를 따로 두면 같은 데이터의
 * 직렬화가 두 벌이 되어, 도구 응답에 필드가 하나 붙을 때마다 이쪽이 조용히 뒤처집니다.</p>
 */
@Builder
public record AgentProjectSummaryRequest(
        Long projectId,
        // 서버 기준 오늘(Asia/Seoul). D-day·지연 판정의 기준점이라 FastAPI가 스스로 정하지 않고 받아 씁니다.
        LocalDate today,
        Project project,
        List<AgentMilestoneListResponse.AgentMilestone> milestones,
        List<AgentTaskListResponse.AgentTask> tasks,
        AgentBudgetSummaryResponse budget,
        List<AgentExpenseListResponse.AgentExpense> expenses,
        List<AgentPostListResponse.AgentPost> posts,
        // meeting.list에 followUp을 더한 모양. 후속 액션 미정리 판정이 meeting 섹션의 핵심 재료입니다.
        List<Meeting> meetings,
        List<UpcomingMeeting> upcomingMeetings
) {
    public record Project(String name, LocalDate startDate, LocalDate targetDate) {
    }

    @Builder
    public record Meeting(
            Long meetingId,
            String title,
            LocalDate meetingDate,
            String authorName,
            String followUp
    ) {
    }

    public record UpcomingMeeting(String title, LocalDateTime startAt) {
    }
}
