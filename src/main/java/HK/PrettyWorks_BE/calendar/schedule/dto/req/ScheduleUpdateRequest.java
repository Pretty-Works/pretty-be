package HK.PrettyWorks_BE.calendar.schedule.dto.req;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleUpdateRequest(
        // 아래 필드는 모두 선택. 미전달(null)이면 기존 값을 유지한다.
        @Size(max = 200, message = "일정 제목은 최대 200자까지 입력 가능합니다.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백만으로 입력할 수 없습니다.")
        String title,

        LocalDateTime startAt,

        LocalDateTime endAt,

        Boolean allDay,

        // 유형(선택). 미전달(null)이면 기존 유지.
        ScheduleType type,

        // null = 참가자 그대로 유지 / 빈 배열 = 작성자 혼자로 축소 / 값 있음 = 그 목록으로 교체.
        List<Long> participantUserIds
) {
}
