package HK.PrettyWorks_BE.calendar.schedule.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleListResponse(
        List<ScheduleItem> schedules
) {

    @Builder
    public record ScheduleItem(
            Long id,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String type,
            boolean isLeave,
            // --- 아래 휴가 전용 필드: 휴가일 때만 값이 있고, 일반 일정이면 null이라 응답에서 생략된다(NON_NULL). ---
            // 휴가 수정/취소 API(PATCH·DELETE /calendar/leaves/{leaveId}) 호출 키. 편집 모달 연동에 필수.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Long leaveId,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String leaveType,
            // 사유(편집 모달 프리필용). 완전공개 정책이라 전원에게 노출. 사유 없이 신청한 휴가는 생략.
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String reason,
            // 신청 일수(시작~종료 포함).
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer days,
            Owner owner,
            List<Participant> participants
    ) {
    }

    @Builder
    public record Owner(
            Long userId,
            String name
    ) {
    }

    @Builder
    public record Participant(
            Long userId,
            String name,
            String role
    ) {
    }
}
