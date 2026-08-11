package HK.PrettyWorks_BE.agent.suggestion.api;

import HK.PrettyWorks_BE.agent.suggestion.application.AgentSuggestionService;
import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 AgentSuggestionApi 인터페이스에 있습니다.
//
// GET인 이유: 저장되는 것이 없는 읽기이고 요청 바디도 없습니다. 재료는 프론트가 보내지 않고
// 토큰의 사용자로 서버가 모읍니다 — 그래서 프론트가 보낼 것은 화면 이름 하나뿐입니다.
// (FastAPI로 나가는 호출만 규격대로 POST입니다. 재료를 바디에 실어야 하기 때문입니다.)
@RestController
@RequiredArgsConstructor
public class AgentSuggestionController implements AgentSuggestionApi {

    private final AgentSuggestionService agentSuggestionService;

    @Override
    @GetMapping("/api/v1/agent/suggestions")
    public ResponseEntity<AgentSuggestionResponse> getSuggestions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String screen
    ) {
        return ResponseEntity.ok(agentSuggestionService.get(userId, screen));
    }
}
