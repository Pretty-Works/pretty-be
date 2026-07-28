package HK.PrettyWorks_BE.calendar.leave.dto.req;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record LeaveCreateRequest(
        @Schema(example = "ANNUAL", description = "휴가 유형 (ANNUAL: 연차, EXCUSED: 공가)")
        @NotNull(message = "휴가 유형을 선택해주세요.")
        LeaveType leaveType,

        @Schema(example = "2026-08-10", description = "시작일 (yyyy-MM-dd)")
        @NotNull(message = "시작일을 입력해주세요.")
        LocalDate startDate,

        @Schema(example = "2026-08-12", description = "종료일 (yyyy-MM-dd). 시작일 이상이어야 함(같은 날 허용)")
        @NotNull(message = "종료일을 입력해주세요.")
        LocalDate endDate,

        @Schema(example = "개인 사정", description = "사유 (선택, 최대 255자)")
        @Size(max = 255, message = "사유는 최대 255자까지 입력 가능합니다.")
        String reason
) {
}
