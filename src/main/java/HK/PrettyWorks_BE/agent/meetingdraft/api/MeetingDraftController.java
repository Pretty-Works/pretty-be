package HK.PrettyWorks_BE.agent.meetingdraft.api;

import HK.PrettyWorks_BE.agent.meetingdraft.application.MeetingDraftService;
import HK.PrettyWorks_BE.agent.meetingdraft.dto.MeetingDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Swagger 문서는 MeetingDraftApi 인터페이스에 있습니다.
//
// 경로는 회의록 하위(/api/v1/projects/{projectId}/meetings/draft)지만 구현은 agent 패키지에 둡니다.
// 이 기능의 본체는 에이전트 서버 호출이고 회의록 도메인은 검증 규칙의 출처일 뿐이라,
// 프로젝트 탭 AI 요약(ProjectSummaryController)과 같은 자리에 둡니다.
//
// 200으로 내려주는 이유: 만들어진 것은 화면에 채울 값이지 리소스가 아닙니다.
// 이 호출로 저장되는 것이 없으므로 201(Created)은 맞지 않습니다.
@RestController
@RequiredArgsConstructor
public class MeetingDraftController implements MeetingDraftApi {

    private final MeetingDraftService meetingDraftService;

    @Override
    @PostMapping(
            value = "/api/v1/projects/{projectId}/meetings/draft",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<MeetingDraftResponse> createMeetingDraft(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(meetingDraftService.draft(userId, projectId, file));
    }
}
