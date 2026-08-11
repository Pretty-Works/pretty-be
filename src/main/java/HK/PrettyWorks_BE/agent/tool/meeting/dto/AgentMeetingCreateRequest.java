package HK.PrettyWorks_BE.agent.tool.meeting.dto;

import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

// meeting.create 요청. 공개 API 바디에 projectId를 더한 형태다.
//
// 경로 변수를 바디에도 싣는 이유: 승인 해시는 바디만 덮는다. projectId가 바디에 없으면
// "프로젝트 A에 회의록"을 승인받고 프로젝트 B로 POST해도 해시가 맞아 통과한다.
// AgentWriteExecutor가 바디의 projectId와 실제 경로를 대조해 그 구멍을 막는다.
//
// 필드 검증은 공개 DTO에 이미 붙어 있으므로 여기서는 필수 여부만 최소로 두고
// 나머지는 toDomain()으로 넘겨 같은 규칙을 타게 한다.
public record AgentMeetingCreateRequest(
        @NotNull Long projectId,
        @NotNull String title,
        @NotNull LocalDate meetingDate,
        String location,
        @NotEmpty List<Long> attendeeIds,
        String purpose,
        String content,
        String followUp,
        String recording
) {
    public MeetingCreateRequest toDomain() {
        return new MeetingCreateRequest(
                title, meetingDate, location, attendeeIds, purpose, content, followUp, recording);
    }
}
