package HK.PrettyWorks_BE.calendar.schedule.dto.req;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleCreateRequest(
        @Schema(example = "주간 팀 회의", description = "일정 제목")
        @NotBlank(message = "일정 제목을 입력해주세요.")
        @Size(max = 200, message = "일정 제목은 최대 200자까지 입력 가능합니다.")
        String title,

        @Schema(example = "2026-08-10T10:00:00", description = "시작일시 (yyyy-MM-dd'T'HH:mm:ss)")
        @NotNull(message = "시작일시를 입력해주세요.")
        LocalDateTime startAt,

        @Schema(example = "2026-08-10T11:00:00", description = "종료일시. 시작일시 이후여야 함(같은 시각 허용)")
        @NotNull(message = "종료일시를 입력해주세요.")
        LocalDateTime endAt,

        // 종일 여부(선택). 원시 boolean은 생략 시 record 역직렬화가 실패하므로 Boolean으로 받는다. null이면 서비스에서 false로 처리.
        @Schema(example = "false", description = "종일 여부(선택, 기본 false). true면 시간이 00:00:00~23:59:59로 정규화됨")
        Boolean allDay,

        @Schema(example = "MEETING", description = "일정 유형 (MEETING: 회의, FIELDWORK: 외근, PERSONAL: 개인)")
        @NotNull(message = "일정 유형을 선택해주세요.")
        ScheduleType type,

        // 참가자 userId 목록(작성자 제외). 생략/빈 배열 허용, 서비스에서 중복 제거·존재 검증합니다.
        @Schema(example = "[2, 3]", description = "참가자 userId 목록(작성자 제외, 선택). 중복·본인은 서버가 정리")
        List<Long> participantUserIds
) {
}
