package HK.PrettyWorks_BE.agent.meetingdraft.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 회의록 작성 화면(Form)을 채울 초안 한 벌.
 *
 * <p>필드 이름을 회의록 작성 API({@code MeetingCreateRequest})와 맞춰 두었습니다. 프론트가 이 응답을
 * 폼에 그대로 밀어 넣고, 사용자가 고친 값을 그대로 작성 API로 보내면 되기 때문입니다.
 * 참석자만 이름이 다른데({@code attendeeUserIds} ↔ {@code attendeeIds}), 규격이 정한 이름입니다.</p>
 *
 * <p><b>저장은 하지 않습니다.</b> 이 API가 만드는 것은 화면에 채울 값뿐이고, 회의록은
 * 사용자가 검토·수정한 뒤 직접 저장합니다.</p>
 */
@Builder
public record MeetingDraftResponse(

        @Schema(example = "스프린트 리뷰", description = "회의 제목. 근거가 없으면 null")
        String title,

        @Schema(example = "2026-08-05", description = "회의 날짜. 형식이 잘못됐거나 저장할 수 없는 날짜면 null")
        LocalDate meetingDate,

        @Schema(example = "회의실 A", description = "장소. 근거가 없으면 null")
        String location,

        @Schema(example = "API 개발 진행 상황 점검", description = "회의 목적. 근거가 없으면 null")
        String purpose,

        @Schema(example = "· API 개발 진행률 확인\n· 일정 조정 논의", description = "주요 내용(불릿). 근거가 없으면 null")
        String content,

        @Schema(example = "· API 명세 작성 — 이하늘, 8월 8일", description = "후속 조치(불릿). 근거가 없으면 null")
        String followUp,

        // 빈 배열일 수 있습니다. null은 내려가지 않습니다 — 프론트가 목록을 다루는 코드에서
        // null 검사를 따로 하지 않아도 되게 합니다.
        @Schema(example = "[1, 2]", description = "참석자 userId 목록. 프로젝트 참여자 중 확인된 사람만")
        List<Long> attendeeUserIds
) {
}
