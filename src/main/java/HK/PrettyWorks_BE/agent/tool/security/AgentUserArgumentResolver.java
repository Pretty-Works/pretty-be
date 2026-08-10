package HK.PrettyWorks_BE.agent.tool.security;

import HK.PrettyWorks_BE.agent.shared.exception.AgentErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AgentUserArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AgentUser.class)
                && parameter.getParameterType() == Long.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object userId = webRequest.getAttribute(InternalAgentAttributes.USER_ID,
                NativeWebRequest.SCOPE_REQUEST);
        if (!(userId instanceof Long)) {
            throw BaseException.type(AgentErrorCode.INVALID_INTERNAL_KEY);
        }
        return userId;
    }
}
