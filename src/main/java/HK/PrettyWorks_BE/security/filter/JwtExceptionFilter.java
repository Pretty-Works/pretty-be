package HK.PrettyWorks_BE.security.filter;

import HK.PrettyWorks_BE.global.base.BaseResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.CustomErrorResponse;
import HK.PrettyWorks_BE.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {

    // 필터는 ResponseBodyAdvice를 거치지 않으므로 직접 JSON 직렬화를 해야 합니다.
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 뒤에 있는 JWT 인증 필터와 나머지 필터 체인을 실행합니다.
            filterChain.doFilter(request, response);
        } catch (BaseException e) {
            // JWT 검증 중 발생한 도메인 예외를 공통 에러 응답으로 변환합니다.
            writeErrorResponse(response, e.getCode(), e);
        }
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode,
            Exception exception
    ) throws IOException {
        // 인증 실패는 흔한 클라이언트 오류이므로 warn 레벨로 남깁니다.
        log.warn("[JWT 인증 실패] {} | {}", errorCode.getErrorCode(), exception.getMessage());

        // ErrorCode에 정의된 HTTP 상태와 JSON 응답 타입을 명시합니다.
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        // 프로젝트의 표준 실패 응답 모양(BaseResponse.fail)으로 감쌉니다.
        CustomErrorResponse errorResponse = CustomErrorResponse.from(errorCode);
        BaseResponse<?> baseResponse = BaseResponse.fail(errorResponse);

        // 필터 단계에서는 컨트롤러 응답 후처리가 동작하지 않으므로 직접 응답 바디를 씁니다.
        response.getWriter().write(objectMapper.writeValueAsString(baseResponse));
    }
}
