package HK.PrettyWorks_BE.global.exception;

import HK.PrettyWorks_BE.global.base.BaseResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

// 필터·Security 진입점처럼 ResponseBodyAdvice를 거치지 않는 위치에서 공통 실패 응답을 직접 쓰는 헬퍼입니다.
// 응답 포맷(BaseResponse)이 바뀌어도 이 클래스만 고치면 됩니다. (로깅은 상황마다 달라 호출부가 담당)
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        // ErrorCode에 정의된 HTTP 상태와 JSON 응답 타입을 명시합니다.
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        // 프로젝트의 표준 실패 응답 모양(BaseResponse.fail)으로 감쌉니다.
        BaseResponse<?> baseResponse = BaseResponse.fail(CustomErrorResponse.from(errorCode));
        response.getWriter().write(objectMapper.writeValueAsString(baseResponse));
    }
}
