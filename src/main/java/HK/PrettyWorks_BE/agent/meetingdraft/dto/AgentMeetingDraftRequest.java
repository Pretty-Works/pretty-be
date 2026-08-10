package HK.PrettyWorks_BE.agent.meetingdraft.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * FastAPI {@code POST /api/agent/meeting-draft} 요청 바디.
 *
 * <p>Run이 아니라 단발 호출이라 {@code X-Run-Id}가 없고, 따라서 FastAPI가 내부 도구를 되불러
 * 재료를 모을 수단이 없습니다. 그래서 초안에 필요한 재료를 여기에 전부 실어 보냅니다
 * (프로젝트 탭 AI 요약과 같은 방식).</p>
 *
 * <p>{@code projectMembers}를 프론트에게서 받지 않고 BE가 직접 조회해 채우는 것이 이 API의
 * 핵심입니다. 참석자 후보 명단이 곧 인가 경계라, 클라이언트가 준 목록을 그대로 실으면
 * 남의 프로젝트 인원을 참석자 후보로 밀어 넣을 수 있습니다.</p>
 */
@Builder
public record AgentMeetingDraftRequest(
        // 업로드한 txt에서 뽑아낸 전문. 상한은 MeetingTranscriptReader가 지킵니다.
        String transcript,
        // 서버 기준 오늘(Asia/Seoul). "어제 회의"처럼 상대 표현을 날짜로 바꾸는 기준점이라
        // FastAPI가 스스로 정하지 않고 받아 씁니다.
        LocalDate today,
        List<ProjectMember> projectMembers
) {
    /**
     * 참석자 후보 한 명. 이 목록에 없는 사람은 초안의 참석자가 될 수 없습니다.
     *
     * <p>부서·직책은 코드가 아니라 한글명입니다(공동 규격 §4-2). 녹취록에 적히는 말이
     * "이하늘 대리"이지 "이하늘 SENIOR"가 아니기 때문입니다.</p>
     */
    @Builder
    public record ProjectMember(
            Long userId,
            String name,
            String department,
            String position
    ) {
    }
}
