package HK.PrettyWorks_BE.security.handler;

import HK.PrettyWorks_BE.global.base.BaseResponse;
import HK.PrettyWorks_BE.global.exception.CustomErrorResponse;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Security 예외 응답은 컨트롤러 밖에서 만들어지므로 ObjectMapper로 직접 JSON을 씁니다.
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // 토큰이 없거나 인증 객체가 없는 요청은 401 Unauthorized로 응답합니다.
        response.setStatus(GlobalErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        // 프로젝트 공통 실패 응답 포맷을 Security 진입점에서도 동일하게 사용합니다.
        CustomErrorResponse errorResponse = CustomErrorResponse.from(GlobalErrorCode.UNAUTHORIZED);
        BaseResponse<?> baseResponse = BaseResponse.fail(errorResponse);

        // ResponseInterceptor를 거치지 않는 위치라서 응답 바디를 직접 작성합니다.
        response.getWriter().write(objectMapper.writeValueAsString(baseResponse));
    }
}
