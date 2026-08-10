package HK.PrettyWorks_BE.security.config;

import HK.PrettyWorks_BE.agent.execution.application.AgentRunEventService;
import HK.PrettyWorks_BE.agent.execution.persistence.AgentRunRepository;
import HK.PrettyWorks_BE.agent.interaction.application.ApprovalTokenService;
import HK.PrettyWorks_BE.agent.tool.security.InternalAgentFilter;
import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.global.exception.ErrorResponseWriter;
import HK.PrettyWorks_BE.security.filter.JwtAuthenticationFilter;
import HK.PrettyWorks_BE.security.filter.JwtExceptionFilter;
import HK.PrettyWorks_BE.security.handler.JwtAuthenticationEntryPoint;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT를 읽어 인증 객체를 만드는 필터입니다.
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // JWT 인증 과정에서 발생한 예외를 공통 응답 포맷으로 변환하는 필터입니다.
    private final JwtExceptionFilter jwtExceptionFilter;

    // 인증 정보가 없는 사용자가 보호된 API에 접근했을 때 401 응답을 만드는 진입점입니다.
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    // 배포 환경별 프론트 주소
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Bean
    public InternalAgentFilter internalAgentFilter(
            @Value("${agent.internal.api-key}") String internalApiKey,
            AgentRunRepository runRepository,
            CurrentUserService currentUserService,
            AgentRunEventService runEventService,
            ApprovalTokenService approvalTokenService,
            ErrorResponseWriter errorResponseWriter
    ) {
        return new InternalAgentFilter(internalApiKey, runRepository, currentUserService,
                runEventService, approvalTokenService, errorResponseWriter);
    }

    // SecurityFilterChain 안에서만 실행하고 서블릿 필터로 중복 등록되지는 않게 한다.
    @Bean
    public FilterRegistrationBean<InternalAgentFilter> internalAgentFilterRegistration(
            InternalAgentFilter internalAgentFilter) {
        FilterRegistrationBean<InternalAgentFilter> registration =
                new FilterRegistrationBean<>(internalAgentFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalAgentSecurityFilterChain(
            HttpSecurity http, InternalAgentFilter internalAgentFilter) throws Exception {
        return http
                .securityMatcher("/api/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(internalAgentFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // JWT 기반 API 서버에서는 브라우저 세션 CSRF 방어가 필요하지 않으므로 비활성화합니다.
                .csrf(AbstractHttpConfigurer::disable)
                // CORS 요청을 허용하기 위해 CORS 설정을 적용합니다.
                .cors(Customizer.withDefaults())
                // Authorization: Basic 방식의 기본 인증 창을 사용하지 않습니다.
                .httpBasic(AbstractHttpConfigurer::disable)
                // 서버 렌더링 로그인 폼 대신 별도 로그인 API와 JWT를 사용합니다.
                .formLogin(AbstractHttpConfigurer::disable)
                // 서버가 세션을 만들지 않고, 매 요청마다 JWT로 인증하도록 설정합니다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 인증 없이 접근 가능한 API만 열어두고, 나머지 요청은 모두 인증을 요구합니다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AuthConstant.AUTH_WHITELIST).permitAll()
                        .anyRequest().authenticated()
                        // SSE(SseEmitter) 응답을 깨뜨리지 않기 위해 ASYNC 디스패치는 인가에서 제외합니다.
                        //
                        // emitter.complete() 를 부르면 톰캣이 응답을 마무리하려고 필터 체인을 ASYNC로
                        // 한 번 더 태웁니다. 이때 JwtAuthenticationFilter 는 OncePerRequestFilter 라
                        // 기본값(shouldNotFilterAsyncDispatch=true)에 따라 건너뛰어 SecurityContext 가 비고,
                        // AuthorizationFilter 만 돌아 Access Denied 가 납니다. 그 시점엔 이미 응답이
                        // 커밋된 뒤라 에러 페이지도 못 쓰고 커넥션이 그냥 끊겨, 브라우저에는 종료 청크가
                        // 빠진 ERR_INCOMPLETE_CHUNKED_ENCODING 으로 보입니다.
                        //
                        // 최초 REQUEST 디스패치에서 이미 인가를 마쳤고 ASYNC 는 그 응답을 닫는 절차일
                        // 뿐이라, 여기서 다시 인가할 실익이 없습니다. (FORWARD/INCLUDE 는 그대로 검사)
                        .withObjectPostProcessor(new ObjectPostProcessor<AuthorizationFilter>() {
                            @Override
                            public <O extends AuthorizationFilter> O postProcess(O filter) {
                                filter.setFilterAsyncDispatch(false);
                                return filter;
                            }
                        })
                )
                // 인증되지 않은 요청이 보호 자원에 접근하면 JSON 형태의 401 응답을 내려줍니다.
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                // JWT 필터에서 던진 BaseException을 잡기 위해 인증 필터보다 앞에 둡니다.
                .addFilterBefore(jwtExceptionFilter, UsernamePasswordAuthenticationFilter.class)
                // 예외 필터 다음에 JWT 인증 필터를 실행해 SecurityContext에 사용자 ID를 저장합니다.
                .addFilterAfter(jwtAuthenticationFilter, JwtExceptionFilter.class)
                .build();
    }

    @Bean
    // 프론트엔드(localhost:3000)에서 오는 브라우저 요청을 허용하기 위한 CORS 설정입니다.
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin을 지정합니다.
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        // 허용할 HTTP 메서드를 지정합니다.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 요청 헤더를 모두 허용합니다. (Authorization 포함)
        configuration.setAllowedHeaders(List.of("*"));
        // 브라우저는 기본적으로 소수의 표준 헤더만 JS에 노출하므로, 추적 ID를 읽으려면 명시해야 합니다.
        configuration.setExposedHeaders(List.of("X-Trace-Id", "X-Run-Id"));
        // 쿠키, Authorization 헤더 등 인증 정보를 포함한 요청을 허용합니다.
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로에 대해 위 CORS 정책을 적용합니다.
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
