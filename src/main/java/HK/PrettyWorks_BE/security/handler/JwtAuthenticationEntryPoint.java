package HK.PrettyWorks_BE.security.handler;

import HK.PrettyWorks_BE.global.exception.ErrorResponseWriter;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Security 예외 응답은 컨트롤러 밖(ResponseInterceptor를 거치지 않는 위치)에서 만들어지므로 공통 헬퍼로 직접 씁니다.
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // 토큰이 없거나 인증 객체가 없는 요청은 401 Unauthorized로 응답합니다.
        errorResponseWriter.write(response, GlobalErrorCode.UNAUTHORIZED);
    }
}
