package HK.PrettyWorks_BE.agent.internal;

import HK.PrettyWorks_BE.agent.domain.AgentRunEntity;
import HK.PrettyWorks_BE.agent.exception.AgentErrorCode;
import HK.PrettyWorks_BE.agent.repository.AgentRunRepository;
import HK.PrettyWorks_BE.agent.service.AgentRunEventService;
import HK.PrettyWorks_BE.agent.service.ApprovalTokenService;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.ErrorResponseWriter;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
public class InternalAgentFilter extends OncePerRequestFilter {
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    public static final String RUN_ID_HEADER = "X-Run-Id";
    public static final String APPROVAL_TOKEN_HEADER = "X-Approval-Token";

    // 승인 해시를 대조하려면 요청 본문 원본을 통째로 들고 있어야 하는데, 상한이 없으면
    // 큰 본문 하나로 힙을 밀어버릴 수 있다. 도구 인자는 회의록 본문이 길어도 수 KB 수준이라
    // 64KB면 넉넉하다(비교: screenContext 상한 32KB).
    private static final int MAX_WRITE_BODY_BYTES = 64 * 1024;

    private final String expectedApiKey;
    private final AgentRunRepository runRepository;
    private final CurrentUserService currentUserService;
    private final AgentRunEventService runEventService;
    private final ApprovalTokenService approvalTokenService;
    private final ErrorResponseWriter errorResponseWriter;

    public InternalAgentFilter(String expectedApiKey, AgentRunRepository runRepository,
                               CurrentUserService currentUserService, AgentRunEventService runEventService,
                               ApprovalTokenService approvalTokenService,
                               ErrorResponseWriter errorResponseWriter) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException("agent.internal.api-key must not be blank");
        }
        this.expectedApiKey = expectedApiKey;
        this.runRepository = runRepository;
        this.currentUserService = currentUserService;
        this.runEventService = runEventService;
        this.approvalTokenService = approvalTokenService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            verifyApiKey(request.getHeader(INTERNAL_KEY_HEADER));
            AgentRunEntity run = activeRun(request.getHeader(RUN_ID_HEADER));
            verifyEmployed(run.getUserId());

            request.setAttribute(InternalAgentAttributes.USER_ID, run.getUserId());
            request.setAttribute(InternalAgentAttributes.INTERNAL_RUN_ID, run.getId());
            request.setAttribute(InternalAgentAttributes.PUBLIC_RUN_ID, run.getRunId());

            if (isWrite(request)) {
                ApprovalTokenService.TokenInspection inspection = approvalTokenService.inspect(
                        request.getHeader(APPROVAL_TOKEN_HEADER), run.getId());
                request.setAttribute(InternalAgentAttributes.TOKEN_INSPECTION, inspection);
                registerToolCall(run);
                verifyBodySize(request);

                ContentCachingRequestWrapper cachedRequest =
                        new ContentCachingRequestWrapper(request, MAX_WRITE_BODY_BYTES);
                cachedRequest.setAttribute(InternalAgentAttributes.CACHED_REQUEST, cachedRequest);
                filterChain.doFilter(cachedRequest, response);
            } else {
                registerToolCall(run);
                filterChain.doFilter(request, response);
            }
        } catch (BaseException exception) {
            if (exception.getCode() == AgentErrorCode.INVALID_INTERNAL_KEY) {
                log.warn("[내부 에이전트 인증 실패] uri={}", request.getRequestURI());
            }
            errorResponseWriter.write(response, exception.getCode());
        }
    }

    // 실행당 20회 상한. 무한 루프와 장애 확산을 끊는 차단기라 실패한 호출과 재시도도 함께 센다
    // ("성공 횟수"가 아니라 "인증된 요청 시도 횟수"다).
    //
    // 다만 WRITE는 승인 토큰을 확인한 뒤에 센다. 앞에 두면 남의 토큰이나 엉터리 토큰으로 보낸
    // 요청까지 정상 실행의 예산을 깎을 수 있다.
    private void registerToolCall(AgentRunEntity run) {
        runEventService.registerToolCall(run.getId());
    }

    private void verifyApiKey(String actualApiKey) {
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualApiKey == null
                ? new byte[0]
                : actualApiKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw BaseException.type(AgentErrorCode.INVALID_INTERNAL_KEY);
        }
    }

    private AgentRunEntity activeRun(String publicRunId) {
        if (publicRunId == null || publicRunId.isBlank()) {
            throw BaseException.type(AgentErrorCode.RUN_NOT_FOUND);
        }
        AgentRunEntity run = runRepository.findByRunId(publicRunId)
                .orElseThrow(() -> BaseException.type(AgentErrorCode.RUN_NOT_FOUND));
        if (!run.getStatus().isActive()) {
            throw BaseException.type(AgentErrorCode.RUN_NOT_FOUND);
        }
        return run;
    }

    private void verifyEmployed(Long userId) {
        currentUserService.getEmployedUser(userId);
    }

    // 본문을 읽기 전에 미리 끊는다. 상한을 넘긴 채로 감싸면 앞부분만 캐시되고, 그 잘린 바이트가
    // 해시 대조에서 AGENT_015 로 나가 원인을 찾을 수 없게 된다.
    //
    // Content-Length 가 없는 chunked 전송은 여기서 걸러지지 않지만, LLM팀 규격이 canonical 문자열을
    // 바이트로 실어 보내는 방식(httpx content=)이라 실제로는 항상 붙는다.
    private void verifyBodySize(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0 || contentLength > MAX_WRITE_BODY_BYTES) {
            throw BaseException.type(AgentErrorCode.WRITE_BODY_TOO_LARGE);
        }
    }

    private boolean isWrite(HttpServletRequest request) {
        String method = request.getMethod();
        return !HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method);
    }
}
