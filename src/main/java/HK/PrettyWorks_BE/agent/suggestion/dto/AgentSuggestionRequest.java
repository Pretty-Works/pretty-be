package HK.PrettyWorks_BE.agent.suggestion.dto;

import HK.PrettyWorks_BE.agent.tool.calendar.dto.AgentLeaveBalanceResponse;
import HK.PrettyWorks_BE.agent.tool.project.dto.AgentProjectSearchResponse;
import HK.PrettyWorks_BE.agent.tool.task.dto.AgentTaskListResponse;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FastAPI {@code POST /api/agent/suggestions} 요청 바디.
 *
 * <p>Run이 아니라 단발 호출이라 {@code X-Run-Id}가 없고, 따라서 FastAPI가 내부 도구를 되불러
 * 재료를 모을 수단이 없습니다. 추천 후보를 고르는 데 필요한 재료를 여기에 전부 실어 보냅니다
 * (프로젝트 AI 요약·회의록 초안 생성과 같은 방식).</p>
 *
 * <p>필드 타입을 새로 정의하지 않고 내부 도구 응답(project.search · task.list · leave.balance)을
 * 그대로 재사용합니다. 추천 전용 DTO를 따로 두면 같은 데이터의 직렬화가 두 벌이 되어,
 * 도구 응답에 필드가 하나 붙을 때마다 이쪽이 조용히 뒤처집니다. 규격에 없는 필드가 더 실려
 * 가는 것은 FastAPI가 무시하므로 문제되지 않습니다.</p>
 */
@Builder
public record AgentSuggestionRequest(
        // 서버 기준 오늘(Asia/Seoul). 지연·임박 판정의 기준점이라 FastAPI가 스스로 정하지 않고 받아 씁니다.
        LocalDate today,
        // 현재 화면(HOME · PROJECT · CALENDAR …). 같은 재료라도 화면에 따라 고를 후보가 달라집니다.
        String screen,
        List<AgentProjectSearchResponse.AgentProject> projects,
        List<AgentTaskListResponse.AgentTask> tasks,
        // meeting.list에 followUp을 더한 모양. 후속 액션 미정리 판정의 유일한 재료입니다.
        List<Meeting> meetings,
        List<UpcomingMeeting> upcomingMeetings,
        AgentLeaveBalanceResponse leaveBalance,
        // 최근 대화 제목. 방금 물어본 것을 또 추천하지 않기 위한 재료라 본문은 보내지 않습니다.
        List<String> recentQuestions
) {
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
