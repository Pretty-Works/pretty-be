package HK.PrettyWorks_BE.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String JSON = "application/json";

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

    /**
     * 문서와 실제 응답의 차이를 메우는 후처리기.
     *
     * <p>ResponseInterceptor(ResponseBodyAdvice)가 모든 응답을 BaseResponse로 감싸는데,
     * springdoc은 컨트롤러 반환 타입만 보므로 문서에는 감싸지 않은 알맹이만 나온다.
     * 그대로 두면 프론트가 result를 빠뜨린 파싱 코드를 짜게 되므로 여기서 스키마를 감싼다.
     *
     * <p>엔드포인트마다 예시를 손으로 적는 방법도 있지만, 그러면 DTO가 바뀔 때마다 예시가 낡는다.
     * 스키마를 조립하면 DTO 변경이 자동으로 반영된다.
     *
     * <p>동작은 전부 '없을 때만 추가'다. 각 API가 직접 선언한 응답은 덮어쓰지 않으므로
     * 도메인별 문서(ScheduleApi 등)에 적힌 내용이 그대로 우선한다.
     */
    @Bean
    public OpenApiCustomizer baseResponseCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            return;
                        }
                        responses.forEach((code, response) -> {
                            if (code.startsWith("2")) {
                                wrapSuccess(response);
                            }
                        });
                        addCommonErrors(responses);
                    });
        };
    }

    // 성공 응답 스키마를 BaseResponse 모양으로 감싼다. 반환 타입이 Void라 본문 스키마가 없으면 result: null로 표기한다.
    private void wrapSuccess(ApiResponse response) {
        Content content = response.getContent();
        if (content == null || content.isEmpty()) {
            response.setContent(new Content()
                    .addMediaType(JSON, new MediaType().schema(wrap(null))));
            return;
        }
        content.values().forEach(mediaType -> mediaType.setSchema(wrap(mediaType.getSchema())));
    }

    private Schema<?> wrap(Schema<?> result) {
        // 이미 감싼 스키마는 건너뛴다 (문서가 두 번 생성돼도 이중 래핑되지 않도록)
        if (result != null && result.getProperties() != null && result.getProperties().containsKey("errorCode")) {
            return result;
        }

        return new ObjectSchema()
                .addProperty("errorCode", new StringSchema()
                        .nullable(true).description("성공 시 null, 실패 시 에러 코드"))
                .addProperty("message", new StringSchema().example("SUCCESS"))
                .addProperty("result", result != null ? result
                        : new ObjectSchema().nullable(true).description("응답 본문이 없는 API는 null"));
    }

    // 모든 API에 공통으로 나는 에러. 엔드포인트마다 적으면 스무 벌 넘게 중복되므로 여기서 한 번에 붙인다.
    // computeIfAbsent라 각 API가 같은 상태코드를 직접 선언했다면 그쪽이 유지된다.
    private void addCommonErrors(ApiResponses responses) {
        responses.computeIfAbsent("401", code -> errorResponse(
                "인증 실패 — 토큰 없음/만료, 또는 로그아웃·도난으로 폐기된 세션",
                "REQUEST_005", "자격 증명이 이루어지지 않았습니다."));
        responses.computeIfAbsent("500", code -> errorResponse(
                "서버 내부 오류",
                "RESPONSE_001", "서버와의 연결에 실패했습니다."));
    }

    private ApiResponse errorResponse(String description, String errorCode, String message) {
        Schema<?> schema = new ObjectSchema()
                .addProperty("errorCode", new StringSchema().example(errorCode))
                .addProperty("message", new StringSchema().example(message))
                .addProperty("result", new ObjectSchema().nullable(true));

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON, new MediaType().schema(schema)));
    }
}
