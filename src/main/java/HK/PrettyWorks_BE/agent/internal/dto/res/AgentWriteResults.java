package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 쓰기 도구 8종의 응답을 한 파일에 모은다.
//
// 각각 레코드 하나짜리 파일 8개로 흩으면 "무엇이 저장됐고 그 결과 무엇이 달라졌는지"라는
// 같은 모양의 계약을 한눈에 볼 수 없다. 공통 원칙은 하나다 —
// 저장된 값 + 저장으로 달라진 파생값(잔여 연차·집행률·완료율)을 함께 준다.
// 에이전트가 그 숫자를 다시 계산하면 서버와 어긋난 답을 말하게 된다.
public final class AgentWriteResults {

    private AgentWriteResults() {
    }

    @Builder
    public record TasksCreated(
            int createdCount,
            // 요청 순서와 동일하다. 에이전트가 "무엇을 몇 건 넣었는지" 그대로 답할 수 있어야 한다.
            List<CreatedTask> tasks
    ) {
        @Builder
        public record CreatedTask(
                Long taskId,
                String content,
                LocalDate dueDate,
                // 개인 할 일이면 null.
                Long projectId
        ) {
        }
    }

    // createdAt은 넣지 않는다. 저장 직후 조회 응답에 없어 별도 질의가 필요한데,
    // 에이전트가 "언제 저장됐는지"를 쓸 일이 없다.
    @Builder
    public record MeetingCreated(
            Long meetingId,
            String documentNo,
            Long projectId,
            String title,
            LocalDate meetingDate
    ) {
    }

    @Builder
    public record ScheduleCreated(
            Long scheduleId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            // 작성자 포함.
            int participantCount
    ) {
    }

    @Builder
    public record ScheduleUpdated(
            Long scheduleId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String type,
            // 참가자를 교체한 경우에만 값이 있다. 미전달(=기존 유지)이면 null이다 —
            // 세려면 조회가 한 번 더 필요한데, 안 바꾼 값을 알려주자고 모든 수정에 쿼리를 붙일 이유가 없다.
            Integer participantCount
    ) {
    }

    @Builder
    public record LeaveSaved(
            Long leaveId,
            // 함께 만들어지는 일정 ID. 휴가가 캘린더에 보이는 근거다.
            Long scheduleId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int days,
            // 신청·수정 후 잔여 일수. 서버가 초과를 막지 않으므로 음수가 될 수 있고,
            // 음수면 에이전트가 답변에 반드시 알려야 한다.
            int remainingDaysAfter
    ) {
    }

    @Builder
    public record ExpenseCreated(
            Long expenseId,
            Long projectId,
            LocalDate expenseDate,
            Long amount,
            Long spentAmountAfter,
            // 목표 예산이 0(제한 없음)이면 null.
            Integer executionRateAfter
    ) {
    }

    @Builder
    public record TaskStatusChanged(
            Long taskId,
            String content,
            boolean completed,
            LocalDateTime completedAt,
            boolean changed
    ) {
    }

    @Builder
    public record MilestoneStatusChanged(
            Long milestoneId,
            String goal,
            boolean completed,
            LocalDateTime completedAt,
            boolean changed,
            int completionRateAfter
    ) {
    }
}
