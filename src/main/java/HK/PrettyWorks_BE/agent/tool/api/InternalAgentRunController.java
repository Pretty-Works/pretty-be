package HK.PrettyWorks_BE.agent.tool.api;

import HK.PrettyWorks_BE.agent.tool.user.AgentUserToolService;
import HK.PrettyWorks_BE.agent.tool.user.dto.AgentRunUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 외부 MCP 서버 전용 실행 조회 엔드포인트.
//
// InternalAgentToolController와 나눈 이유: 저쪽 메서드는 전부 @AgentUser로 userId를 주입받는데,
// 그 값은 InternalAgentFilter가 X-Run-Id를 역산해 심어 준 것이다. 여기는 그 역산 자체를
// 제공하는 쪽이라 @AgentUser를 쓸 수 없다. 규약이 다른 메서드를 한 클래스에 섞지 않는다.
//
// API 키 검증과 BaseResponse 래핑은 다른 내부 엔드포인트와 동일하게 공용 인프라가 처리한다.
@RestController
@RequiredArgsConstructor
public class InternalAgentRunController implements InternalAgentRunApi {

    private final AgentUserToolService userToolService;

    @Override
    @GetMapping("/api/internal/agent/runs/{runId}/user")
    public ResponseEntity<AgentRunUserResponse> runUser(@PathVariable String runId) {
        return ResponseEntity.ok(userToolService.runUser(runId));
    }
}
