package HK.PrettyWorks_BE.project.meeting.dto.res;

import lombok.Builder;
import java.time.LocalDate;
import java.util.List;

@Builder
public record MeetingDetailResponse(
        Long meetingId,
        Long version,
        String documentNumber,
        String title,
        LocalDate meetingDate,
        String location,
        PersonInfo author,
        List<PersonInfo> attendees,
        String recording,
        String purpose,
        String content,
        String followUp
) {
    // 작성자/참석자 공통으로 쓰는 작은 DTO (이름 + 부서)
    @Builder
    public record PersonInfo(
            Long userId,
            String name,
            String department
    ) {
    }
}
