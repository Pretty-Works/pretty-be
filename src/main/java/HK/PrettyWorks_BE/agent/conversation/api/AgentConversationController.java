package HK.PrettyWorks_BE.agent.conversation.api;

import HK.PrettyWorks_BE.agent.conversation.api.request.AgentAutoApproveRequest;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationDeleteResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationListResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationReadResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.conversation.application.AgentConversationService;
import HK.PrettyWorks_BE.agent.conversation.application.AgentQueryService;
import HK.PrettyWorks_BE.global.base.CursorResponse;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 AgentConversationApi 인터페이스에 있습니다.
// 파라미터 제약을 붙이지 않는 이유는 ProjectController 주석 참고.
@RestController
@RequiredArgsConstructor
public class AgentConversationController implements AgentConversationApi {

    private final AgentQueryService agentQueryService;
    private final AgentConversationService agentConversationService;

    // 패널 햄버거(☰)의 대화 내역. "최근 대화" 3건은 size=3으로 같은 API를 쓴다.
    // 무한 스크롤이라 page가 아니라 cursor로 끊는다 — 이유는 AgentQueryService 주석 참고.
    @Override
    @GetMapping("/api/v1/agent/conversations")
    public ResponseEntity<CursorResponse<AgentConversationListResponse, String>> getConversations(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 번에 가져올 개수 (1~100). 패널의 '최근 대화'는 3, 전체보기는 기본값을 씁니다.", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        // 잘못된 size(size<1, size>100)와 손댄 cursor는 서비스가 400으로 거부한다
        return ResponseEntity.ok(agentQueryService.getConversations(userId, cursor, size));
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

    // 대화를 열었을 때 '새 답장' 표시를 끈다. 상세 조회와 분리한 이유는 AgentConversationService 주석 참고.
    @Override
    @PatchMapping("/api/v1/agent/conversations/{conversationId}/read")
    public ResponseEntity<AgentConversationReadResponse> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(agentConversationService.markRead(userId, conversationId));
    }

    // 대화 내역에서 고른 대화 하나를 지운다.
    @Override
    @DeleteMapping("/api/v1/agent/conversations/{conversationId}")
    public ResponseEntity<AgentConversationDeleteResponse> deleteConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(
                agentConversationService.deleteConversation(userId, conversationId));
    }

    @Override
    @PatchMapping("/api/v1/agent/conversations/{conversationId}/auto-approve")
    public ResponseEntity<AgentAutoApproveResponse> changeAutoApprove(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody AgentAutoApproveRequest request
    ) {
        return ResponseEntity.ok(agentConversationService.changeAutoApprove(
                userId, conversationId, request.autoApprove()));
    }
}
