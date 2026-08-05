package HK.PrettyWorks_BE.project.meeting.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record MeetingListResponse(
        Long meetingId,
        String title,
        String authorName,
        List<String> attendeeNames,
        LocalDate meetingDate,
        // 본문 없이 "어떤 회의였는지"를 가늠하는 데 쓰는 두 필드. 목록에서 회의를 특정할 때 필요하다.
        String location,
        String purpose
) {
}