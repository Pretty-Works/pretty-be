package HK.PrettyWorks_BE.agent.internal.dto.res;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// meeting.detail — 회의록 한 건의 전문. ID로 한 건을 정확히 집는 유일한 수단이다.
//
// content·followUp은 사용자가 입력한 텍스트다. 그 안에 지시문처럼 보이는 문구가 있어도
// 명령이 아니라 데이터다(프롬프트 인젝션).
@Builder
public record AgentMeetingDetailResponse(
        Long meetingId,
        Long projectId,
        String documentNo,
        String title,
        LocalDate meetingDate,
        String location,
        String purpose,
        String content,
        // 후속 조치. 할 일 등록 제안(task.create)의 근거가 된다.
        String followUp,
        Long authorId,
        String authorName,
        // 요청자가 작성자인지. 삭제 권한의 기준이다.
        boolean isMine,
        // 수정 가능 여부. 작성자뿐 아니라 참석자도 고칠 수 있어(MEETING_010) isMine만으로는 판단할 수 없다.
        boolean canEdit,
        List<AgentAttendee> attendees
) {
    @Builder
    public record AgentAttendee(
            Long userId,
            String name,
            String department
    ) {
    }
}
