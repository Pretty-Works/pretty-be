package HK.PrettyWorks_BE.project.meeting.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record MeetingCreateRequest(

        @Schema(example = "프로젝트 킥오프 회의", description = "회의명")
        @NotBlank(message = "회의명은 필수입니다.")
        @Size(max = 200, message = "회의명은 200자 이하여야 합니다.")
        String title,

        @Schema(example = "2026-07-20", description = "회의 일자")
        @NotNull(message = "회의 일자는 필수입니다.")
        @PastOrPresent(message = "회의 일자는 오늘이거나 과거여야 합니다.")
        LocalDate meetingDate,

        @Schema(example = "회의실 A", description = "장소")
        @Size(max = 100, message = "장소는 100자 이하여야 합니다.")
        String location,

        @Schema(example = "[2, 3, 4]", description = "참석자 id 목록")
        @NotEmpty(message = "참석자는 최소 1명 이상이어야 합니다.")
        List<Long> attendeeIds,

        @Schema(example = "프로젝트 진행 방향 논의", description = "회의 목적")
        @Size(max = 500, message = "회의 목적은 500자 이하여야 합니다.")
        String purpose,

        @Schema(example = "일정 및 역할 분담 논의", description = "주요 내용")
        String content,

        @Schema(example = "API 명세 작성 후 공유", description = "후속 조치")
        String followUp,

        @Schema(example = "voice1.mp3", description = "녹취 파일명")
        @Size(max = 500, message = "녹취 파일명은 500자 이하여야 합니다.")
        String recording
) {
}