package HK.PrettyWorks_BE.agent.tool.common;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 쓰기 도구의 응답을 한 파일에 모은다.
//
// 각각 레코드 하나짜리 파일로 흩으면 "무엇이 저장됐고 그 결과 무엇이 달라졌는지"라는
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

    // 회의록과 달리 저장 후 다시 읽지 않는다. 서버가 붙이는 파생값(문서번호 같은)이 없어
    // 저장된 값이 곧 요청값이다.
    @Builder
    public record PostCreated(
            Long postId,
            Long projectId,
            String title,
            String priority
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

    // 저장 단계의 결과. 이 시점에 프로젝트 데이터는 하나도 바뀌지 않았다 —
    // 에이전트는 이 목록을 그대로 읽어 "몇 번으로 하시겠어요"를 물으면 된다.
    @Builder
    public record ReplanCreated(
            Long replanId,
            Long projectId,
            List<ReplanScenario> scenarios
    ) {
        @Builder
        public record ReplanScenario(
                String scenarioType,
                // 한글 이름. 에이전트가 코드값을 그대로 말하지 않게 함께 준다.
                String scenarioLabel,
                String summary,
                String risk,
                // 이 시나리오가 바꿀 건수. "얼마나 큰 변경인가"를 가늠하는 값이다.
                int operationCount
        ) {
        }
    }

    // 적용 결과. 종류별 건수를 서버가 세어 준다 —
    // 이미 그 값이던 항목은 건너뛰므로, 에이전트가 요청 건수로 답하면 사실과 어긋난다.
    @Builder
    public record ReplanApplied(
            Long replanId,
            Long projectId,
            String scenarioType,
            String scenarioLabel,
            // 프로젝트 목표일을 옮긴 경우에만 값이 있다.
            LocalDate projectTargetDate,
            int milestoneDateChangedCount,
            int taskDueDateChangedCount,
            int taskCreatedCount,
            // 하드 삭제라 되돌릴 수 없다. 답변에서 반드시 언급해야 하는 숫자다.
            // 담당자를 넘긴 재배치도 여기 잡힌다 — 지우고 새로 만드는 방식이라서다.
            int taskDeletedCount,
            int memberAddedCount
    ) {
    }
}
