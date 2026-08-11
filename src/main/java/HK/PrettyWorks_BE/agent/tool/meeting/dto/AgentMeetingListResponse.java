package HK.PrettyWorks_BE.agent.tool.meeting.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// meeting.list — "어떤 회의들이 있었나"만 답한다.
//
// 본문(content·followUp)은 내려가지 않는다. 본문이 긴 회의록 여러 건을 한 번에 받으면
// 컨텍스트가 터지므로, 한 건의 전문이 필요하면 여기서 meetingId를 찾아 meeting.detail을 부른다.
@Builder
public record AgentMeetingListResponse(
        Long projectId,
        List<AgentMeeting> meetings,
        int totalCount,
        boolean truncated
) {
    @Builder
    public record AgentMeeting(
            Long meetingId,
            String title,
            LocalDate meetingDate,
            String location,
            String purpose,
            String authorName,
            // 작성 시점 스냅샷. 지금 이름이 바뀌었어도 회의록에 적힌 대로 나온다.
            List<String> attendeeNames
    ) {
    }
}
