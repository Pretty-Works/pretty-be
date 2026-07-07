package HK.PrettyWorks_BE.global.interceptor.post;

import HK.PrettyWorks_BE.global.base.BaseResponse;
import HK.PrettyWorks_BE.global.exception.CustomErrorResponse;
import lombok.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// 컨트롤러가 반환한 값을 실제 응답으로 쓰기 직전에 BaseResponse 형태로 감싸는 전역 후처리기입니다.
@RestControllerAdvice(basePackages = "hankyung.tossinvoice")
public class ResponseInterceptor implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(
            MethodParameter returnType,
            @NonNull Class converterType
    ) {
        // 이미 BaseResponse를 반환한 경우에는 이중 래핑을 막습니다.
        return !(returnType.getParameterType() == BaseResponse.class)
                // JSON 응답을 만드는 converter일 때만 응답 래핑을 적용합니다.
                && JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // 예외 핸들러가 만든 CustomErrorResponse는 실패 응답 형태로 감쌉니다.
        if(body instanceof CustomErrorResponse)
            return BaseResponse.fail((CustomErrorResponse)body);

        // 일반 컨트롤러 응답은 성공 응답 형태로 감쌉니다.
        return BaseResponse.success(body);
    }
}
