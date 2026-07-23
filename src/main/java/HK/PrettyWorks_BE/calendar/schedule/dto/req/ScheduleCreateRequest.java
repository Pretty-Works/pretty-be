package HK.PrettyWorks_BE.calendar.schedule.dto.req;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ScheduleCreateRequest(
        @NotBlank(message = "일정 제목을 입력해주세요.")
        @Size(max = 200, message = "일정 제목은 최대 200자까지 입력 가능합니다.")
        String title,

        @NotNull(message = "시작일시를 입력해주세요.")
        LocalDateTime startAt,

        @NotNull(message = "종료일시를 입력해주세요.")
        LocalDateTime endAt,

        // 종일 여부(선택). 원시 boolean은 생략 시 record 역직렬화가 실패하므로 Boolean으로 받는다. null이면 서비스에서 false로 처리.
        Boolean allDay,

        @NotNull(message = "일정 유형을 선택해주세요.")
        ScheduleType type,

        // 참가자 userId 목록(작성자 제외). 생략/빈 배열 허용, 서비스에서 중복 제거·존재 검증합니다.
        List<Long> participantUserIds
) {
}
