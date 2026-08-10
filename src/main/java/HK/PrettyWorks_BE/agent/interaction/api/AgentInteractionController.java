package HK.PrettyWorks_BE.agent.interaction.api;

import HK.PrettyWorks_BE.agent.conversation.application.AgentQueryService;
import HK.PrettyWorks_BE.agent.execution.api.AgentExecutionApi;
import HK.PrettyWorks_BE.agent.execution.streaming.AgentStartedStream;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.interaction.api.response.AgentPendingInteractionsResponse;
import HK.PrettyWorks_BE.agent.interaction.application.AgentResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Swagger 문서는 AgentInteractionApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
@RestController
@RequiredArgsConstructor
public class AgentInteractionController implements AgentInteractionApi {

    private final AgentResumeService agentResumeService;
    private final AgentQueryService agentQueryService;

    @Override
    @PostMapping("/api/v1/agent/approvals/{approvalId}")
    public ResponseEntity<SseEmitter> resolveApproval(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long approvalId,
            @Valid @RequestBody AgentApprovalRequest request
    ) {
        return streamResponse(agentResumeService.resumeApproval(userId, approvalId, request));
    }

    @Override
    @PostMapping("/api/v1/agent/questions/{questionId}")
    public ResponseEntity<SseEmitter> answerQuestion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questionId,
            @Valid @RequestBody AgentQuestionAnswerRequest request
    ) {
        return streamResponse(agentResumeService.resumeQuestion(userId, questionId, request));
    }

    // 대기 카드는 대화가 아니라 홈 화면이 쓰지만, 조회 쿼리는 대화 쪽 조회 서비스에 모여 있다.
    @Override
    @GetMapping("/api/v1/agent/pending-interactions")
    public ResponseEntity<AgentPendingInteractionsResponse> getPendingInteractions(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(agentQueryService.getPendingInteractions(userId));
    }

    // 실행 재개도 실행 시작과 똑같은 SSE 응답이라 헤더 이름을 AgentExecutionApi에서 가져다 쓴다.
    private ResponseEntity<SseEmitter> streamResponse(AgentStartedStream started) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(AgentExecutionApi.RUN_ID_HEADER, started.runId())
                .body(started.emitter());
    }
}
