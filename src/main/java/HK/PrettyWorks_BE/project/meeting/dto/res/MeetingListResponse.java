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
        LocalDate meetingDate
) {
}