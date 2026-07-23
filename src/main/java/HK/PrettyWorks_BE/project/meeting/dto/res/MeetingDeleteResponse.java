package HK.PrettyWorks_BE.project.meeting.dto.res;

import lombok.Builder;

@Builder
public record MeetingDeleteResponse(
        Long meetingId
) {
}