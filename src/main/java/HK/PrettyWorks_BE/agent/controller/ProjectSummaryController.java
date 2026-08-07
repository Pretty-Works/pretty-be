package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.res.ProjectSummaryResponse;
import HK.PrettyWorks_BE.agent.service.ProjectSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 ProjectSummaryApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
//
// 경로는 프로젝트 하위(/api/v1/projects/**)지만 구현은 agent 패키지에 둡니다.
// 이 기능의 본체는 에이전트 서버 호출과 그 응답의 보관이고, 프로젝트 도메인은 재료 제공자입니다.
@RestController
@RequiredArgsConstructor
public class ProjectSummaryController implements ProjectSummaryApi {

    private final ProjectSummaryService projectSummaryService;

    // 프론트가 부르는 것은 사실상 이것 하나다. 보통은 저장된 것을 읽지만,
    // 아직 없으면 그 자리에서 만들어 준다(read-through) — 생성 API를 따로 부를 필요가 없다.
    @Override
    @GetMapping("/api/v1/projects/{projectId}/summary")
    public ResponseEntity<ProjectSummaryResponse> getSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String section
    ) {
        return ResponseEntity.ok(projectSummaryService.get(userId, projectId, section));
    }

    // 선택적 강제 갱신. 화면 진입에는 필요 없고, "방금 바꿨으니 배너도 다시"일 때만 쓴다.
    // 최소 간격 안이면 저장된 배너를 그대로 돌려주므로 연타해도 안전하다.
    @Override
    @PostMapping("/api/v1/projects/{projectId}/summary")
    public ResponseEntity<ProjectSummaryResponse> refreshSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectSummaryService.refresh(userId, projectId));
    }
}
