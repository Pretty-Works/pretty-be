package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.req.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentAutoApproveRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.dto.res.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationsResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentPendingInteractionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "에이전트", description = "에이전트 v2 대화 및 실행 API")
public interface AgentApi {

    @Operation(
            summary = "에이전트 실행 시작",
            description = "메시지와 화면 문맥으로 Run을 만들고 FastAPI 이벤트를 SSE로 전달합니다. "
                    + "응답 헤더 X-Run-Id는 이후 재연결에 사용합니다."
    )
    ResponseEntity<SseEmitter> send(Long userId, Authentication authentication,
                                    AgentMessageRequest request);

    @Operation(summary = "승인 요청 응답 및 실행 재개")
    ResponseEntity<SseEmitter> resolveApproval(Long userId, Long approvalId,
                                               AgentApprovalRequest request);

    @Operation(summary = "질문 응답 및 실행 재개")
    ResponseEntity<SseEmitter> answerQuestion(Long userId, Long questionId,
                                              AgentQuestionAnswerRequest request);

    @Operation(summary = "에이전트 실행 스트림 재연결")
    ResponseEntity<SseEmitter> reconnect(Long userId, String runId, String lastEventId);

    @Operation(summary = "에이전트 실행 취소")
    ResponseEntity<AgentCancelResponse> cancel(Long userId, String runId);

    @Operation(summary = "응답 대기 중인 승인·질문 조회")
    ResponseEntity<AgentPendingInteractionsResponse> getPendingInteractions(Long userId);

    @Operation(summary = "에이전트 대화 목록 조회")
    ResponseEntity<AgentConversationsResponse> getConversations(Long userId, int size);

    @Operation(summary = "에이전트 대화 메시지 조회")
    ResponseEntity<AgentMessagesResponse> getMessages(Long userId, Long conversationId);

    @Operation(summary = "대화 자동 승인 모드 전환")
    ResponseEntity<AgentAutoApproveResponse> changeAutoApprove(
            Long userId, Long conversationId, AgentAutoApproveRequest request);
}
