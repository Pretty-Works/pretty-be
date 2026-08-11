package HK.PrettyWorks_BE.agent.tool.security;

import HK.PrettyWorks_BE.agent.execution.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.execution.application.AgentRunEventService;
import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.global.exception.ErrorResponseWriter;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalAgentFilterTest {

    private static final String API_KEY = "internal-test-key";
    private static final String RUN_USER_URI = "/api/internal/agent/runs/run-1/user";

    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AgentRunEventService runEventService = mock(AgentRunEventService.class);
    private final ApprovalTokenService approvalTokenService = mock(ApprovalTokenService.class);
    private final ErrorResponseWriter errorResponseWriter = mock(ErrorResponseWriter.class);

    private final InternalAgentFilter filter = new InternalAgentFilter(
            API_KEY, runRepository, currentUserService, runEventService,
            approvalTokenService, errorResponseWriter);

    @Test
    void letsRunUserLookupThroughWithoutTheRunIdHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authorized(RUN_USER_URI), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        // 역산은 컨트롤러가 경로의 runId로 직접 한다. 필터가 헤더를 요구하면 그 호출이 통째로 막힌다.
        verifyNoInteractions(runRepository);
    }

    @Test
    void doesNotSpendToolCallBudgetOnRunUserLookup() throws Exception {
        // 툴콜 20회 상한은 에이전트 폭주를 끊는 차단기다. 신원 확인은 에이전트가 한 작업이 아니라서
        // 여기서 세면 진짜 도구 호출이 쓸 예산이 줄어든다.
        filter.doFilter(authorized(RUN_USER_URI), new MockHttpServletResponse(), new MockFilterChain());

        verify(runEventService, never()).registerToolCall(any());
    }

    @Test
    void doesNotSkipChecksForDeeperPathsThatMerelyEndWithUser() throws Exception {
        // 세그먼트 경계를 안 보면 이런 경로까지 검사를 건너뛴다. runId 자리는 한 칸이어야 한다.
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authorized("/api/internal/agent/runs/run-1/messages/user"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
        verify(errorResponseWriter).write(any(), eq(AgentErrorCode.RUN_NOT_FOUND));
    }

    @Test
    void stillRequiresTheApiKeyOnRunUserLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", RUN_USER_URI);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
        verify(errorResponseWriter).write(any(), eq(AgentErrorCode.INVALID_INTERNAL_KEY));
    }

    @Test
    void keepsRunResolutionAndToolCallCountingForOrdinaryToolCalls() throws Exception {
        AgentRunEntity run = AgentRunEntity.builder()
                .runId("run-1")
                .conversationId(1L)
                .userId(9L)
                .goal("이번 주 일정 알려줘")
                .startedAt(LocalDateTime.of(2026, 8, 10, 11, 0))
                .build();
        when(runRepository.findByRunId("run-1")).thenReturn(Optional.of(run));

        MockHttpServletRequest request = authorized("/api/internal/agent/me");
        request.addHeader(InternalAgentFilter.RUN_ID_HEADER, "run-1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(InternalAgentAttributes.USER_ID)).isEqualTo(9L);
        verify(runEventService).registerToolCall(any());
        verify(currentUserService).getEmployedUser(9L);
    }

    private MockHttpServletRequest authorized(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader(InternalAgentFilter.INTERNAL_KEY_HEADER, API_KEY);
        return request;
    }
}
