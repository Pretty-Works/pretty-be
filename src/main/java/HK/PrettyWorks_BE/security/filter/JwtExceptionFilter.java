package HK.PrettyWorks_BE.security.filter;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.ErrorCode;
import HK.PrettyWorks_BE.global.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {

    // 필터는 ResponseBodyAdvice를 거치지 않으므로 공통 헬퍼로 직접 JSON을 씁니다.
    private final ErrorResponseWriter errorResponseWriter;

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
            ErrorCode errorCode = e.getCode();

            // 인증 실패는 흔한 클라이언트 오류이므로 warn 레벨로 남깁니다.
            log.warn("[JWT 인증 실패] {} | {}", errorCode.getErrorCode(), e.getMessage());

            errorResponseWriter.write(response, errorCode);
        }
    }
}
