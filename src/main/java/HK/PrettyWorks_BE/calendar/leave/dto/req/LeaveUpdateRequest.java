package HK.PrettyWorks_BE.calendar.leave.dto.req;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record LeaveUpdateRequest(
        // 아래 필드는 모두 선택. 미전달(null)이면 기존 값을 유지한다.
        @Schema(example = "SICK", description = "휴가 유형(선택, ANNUAL/SICK). 미전달 시 기존 유지")
        LeaveType leaveType,

        @Schema(example = "2026-08-11", description = "시작일(선택). 미전달 시 기존 유지")
        LocalDate startDate,

        @Schema(example = "2026-08-13", description = "종료일(선택). 미전달 시 기존 유지")
        LocalDate endDate,

        // null = 기존 사유 유지 / "" = 사유 비우기
        @Schema(example = "사유 변경", description = "사유(선택, 최대 255자). null=기존 유지, 빈 문자열(\"\")=사유 비우기")
        @Size(max = 255, message = "사유는 최대 255자까지 입력 가능합니다.")
        String reason
) {
}
