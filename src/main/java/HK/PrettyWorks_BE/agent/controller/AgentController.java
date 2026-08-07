package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.req.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentAutoApproveRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.dto.res.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationListResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationReadResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentPendingInteractionsResponse;
import HK.PrettyWorks_BE.agent.service.AgentControlService;
import HK.PrettyWorks_BE.agent.service.AgentExecutionService;
import HK.PrettyWorks_BE.agent.service.AgentQueryService;
import HK.PrettyWorks_BE.agent.service.AgentResumeService;
import HK.PrettyWorks_BE.agent.service.AgentStartedStream;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.security.info.UserAuthentication;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Swagger 문서는 AgentApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
@RestController
@RequiredArgsConstructor
public class AgentController implements AgentApi {

    private static final String RUN_ID_HEADER = "X-Run-Id";

    private final AgentExecutionService agentExecutionService;
    private final AgentResumeService agentResumeService;
    private final AgentQueryService agentQueryService;
    private final AgentControlService agentControlService;

    // 채팅창 전송. conversationId가 null이면 새 스레드를 만들고, 있으면 그 대화를 이어간다.
    @Override
    @PostMapping("/api/v1/agent/messages")
    public ResponseEntity<SseEmitter> send(
            @AuthenticationPrincipal Long userId,
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody AgentMessageRequest request
    ) {
        String sessionId = ((UserAuthentication) authentication).getSessionId();
        AgentStartedStream started =
                agentExecutionService.start(userId, sessionId, request);
        return streamResponse(started);
    }

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

    @Override
    @GetMapping("/api/v1/agent/pending-interactions")
    public ResponseEntity<AgentPendingInteractionsResponse> getPendingInteractions(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(agentQueryService.getPendingInteractions(userId));
    }

    // 패널 햄버거(☰)의 대화 내역. "최근 대화" 3건은 size=3으로 같은 API를 쓴다.
    @Override
    @GetMapping("/api/v1/agent/conversations")
    public ResponseEntity<PageResponse<AgentConversationListResponse>> getConversations(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지당 개수 (1~100). 패널의 '최근 대화'는 3, 전체보기는 기본값을 씁니다.", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        // 잘못된 page/size(page<0, size<1, size>100)는 PageRequests가 400으로 거부한다
        return ResponseEntity.ok(
                agentQueryService.getConversations(userId, PageRequests.of(page, size)));
    }

    // 대화 내역에서 하나를 골랐을 때 말풍선 복원. 오래된 순으로 내려간다.
    @Override
    @GetMapping("/api/v1/agent/conversations/{conversationId}/messages")
    public ResponseEntity<AgentMessagesResponse> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        AgentMessagesResponse response = agentQueryService.getMessages(userId, conversationId);

        return ResponseEntity.ok(response);
    }

    // 대화를 열었을 때 '새 답장' 표시를 끈다. 상세 조회와 분리한 이유는 AgentControlService 주석 참고.
    @Override
    @PatchMapping("/api/v1/agent/conversations/{conversationId}/read")
    public ResponseEntity<AgentConversationReadResponse> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(agentControlService.markRead(userId, conversationId));
    }

    @Override
    @PatchMapping("/api/v1/agent/conversations/{conversationId}/auto-approve")
    public ResponseEntity<AgentAutoApproveResponse> changeAutoApprove(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody AgentAutoApproveRequest request
    ) {
        return ResponseEntity.ok(agentControlService.changeAutoApprove(
                userId, conversationId, request.autoApprove()));
    }

    private ResponseEntity<SseEmitter> streamResponse(AgentStartedStream started) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(RUN_ID_HEADER, started.runId())
                .body(started.emitter());
    }
}
