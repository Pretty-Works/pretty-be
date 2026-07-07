package HK.PrettyWorks_BE.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// spring.web.resources.add-mappings=false 환경에서 테스트 페이지(test.html)만 정적 리소스로 노출합니다.
// 테스트 종료 후 이 파일은 삭제하세요.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/test.html")
                .addResourceLocations("classpath:/static/");
    }
}
