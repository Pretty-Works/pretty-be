package HK.PrettyWorks_BE.calendar.schedule.dto.req;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleUpdateRequest(
        // 아래 필드는 모두 선택. 미전달(null)이면 기존 값을 유지한다.
        @Schema(example = "주간 팀 회의(수정)", description = "제목(선택). 미전달 시 기존 유지. 공백만은 불가")
        @Size(max = 200, message = "일정 제목은 최대 200자까지 입력 가능합니다.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백만으로 입력할 수 없습니다.")
        String title,

        @Schema(example = "2026-08-10T14:00:00", description = "시작일시(선택). 미전달 시 기존 유지")
        LocalDateTime startAt,

        @Schema(example = "2026-08-10T15:00:00", description = "종료일시(선택). 미전달 시 기존 유지")
        LocalDateTime endAt,

        @Schema(example = "false", description = "종일 여부(선택). 미전달 시 기존 유지")
        Boolean allDay,

        // 유형(선택). 미전달(null)이면 기존 유지.
        @Schema(example = "FIELDWORK", description = "유형(선택, MEETING/FIELDWORK/PERSONAL). 미전달 시 기존 유지")
        ScheduleType type,

        // null = 참가자 그대로 유지 / 빈 배열 = 작성자 혼자로 축소 / 값 있음 = 그 목록으로 교체.
        @Schema(example = "[2, 3]", description = "참가자 교체(선택). null=유지, []=작성자 혼자로 축소, 값 있음=그 목록으로 교체")
        List<Long> participantUserIds
) {
}
