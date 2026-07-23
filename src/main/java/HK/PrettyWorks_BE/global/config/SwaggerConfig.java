package HK.PrettyWorks_BE.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI prettyWorksOpenAPI() {
        // Authorize 버튼에 쓸 보안 스킴 이름 (아무 이름이나 가능)
        final String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("PrettyWorks API")
                        .version("V1")
                        .description("PrettyWorks API TEST"))
                // 모든 API에 이 보안 스킴을 적용 → 우측 상단 Authorize 버튼 생성
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)   // HTTP 인증 방식
                                .scheme("bearer")                 // Bearer 토큰
                                .bearerFormat("JWT")));           // 포맷 표기(JWT)
    }
}