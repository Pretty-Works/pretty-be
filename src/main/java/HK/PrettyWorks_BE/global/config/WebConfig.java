package HK.PrettyWorks_BE.global.config;

import HK.PrettyWorks_BE.agent.internal.AgentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.List;

// spring.web.resources.add-mappings=false 환경에서 테스트 페이지(test.html)만 정적 리소스로 노출합니다.
// 테스트 종료 후 이 파일은 삭제하세요.
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AgentUserArgumentResolver agentUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(agentUserArgumentResolver);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/test.html")
                .addResourceLocations("classpath:/static/");
    }
}
