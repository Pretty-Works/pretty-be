package HK.PrettyWorks_BE.agent.execution.api;

import HK.PrettyWorks_BE.agent.execution.api.request.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.execution.api.response.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.execution.application.AgentControlService;
import HK.PrettyWorks_BE.agent.execution.application.AgentExecutionService;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.security.info.UserAuthentication;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// Swagger 문서는 AgentExecutionApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
@RestController
@RequiredArgsConstructor
public class AgentExecutionController implements AgentExecutionApi {

    private final AgentExecutionService agentExecutionService;
    private final AgentControlService agentControlService;

    // 채팅창 전송. conversationId가 null이면 새 스레드를 만들고, 있으면 그 대화를 이어간다.
    //
    // 본문이 JSON 하나였다가 multipart가 됐다. 파일을 실을 자리가 필요해서다 —
    // JSON은 바이너리를 담지 못하고, Base64로 욱여넣으면 프론트가 인코딩을 떠안는다.
    // request 파트가 예전 바디 그대로이고 files 파트만 늘었다. files는 없으면 그만이라 required=false.
    @Override
    @PostMapping(value = "/api/v1/agent/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SseEmitter> send(
            @AuthenticationPrincipal Long userId,
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestPart("request") AgentMessageRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        String sessionId = ((UserAuthentication) authentication).getSessionId();
        AgentStartedStream started =
                agentExecutionService.start(userId, sessionId, request, files);
        return streamResponse(started);
    }

    @Override
    @GetMapping("/api/v1/agent/runs/{runId}/stream")
    public ResponseEntity<SseEmitter> reconnect(
            @AuthenticationPrincipal Long userId,
            @PathVariable String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return streamResponse(agentControlService.reconnect(userId, runId, lastEventId));
    }

    @Override
    @PostMapping("/api/v1/agent/runs/{runId}/cancel")
    public ResponseEntity<AgentCancelResponse> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable String runId
    ) {
        return ResponseEntity.ok(agentControlService.cancel(userId, runId));
    }

    private ResponseEntity<SseEmitter> streamResponse(AgentStartedStream started) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(RUN_ID_HEADER, started.runId())
                .body(started.emitter());
    }
}
