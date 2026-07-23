package HK.PrettyWorks_BE.security.config;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.security.filter.JwtAuthenticationFilter;
import HK.PrettyWorks_BE.security.filter.JwtExceptionFilter;
import HK.PrettyWorks_BE.security.handler.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT를 읽어 인증 객체를 만드는 필터입니다.
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // JWT 인증 과정에서 발생한 예외를 공통 응답 포맷으로 변환하는 필터입니다.
    private final JwtExceptionFilter jwtExceptionFilter;

    // 인증 정보가 없는 사용자가 보호된 API에 접근했을 때 401 응답을 만드는 진입점입니다.
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // JWT 기반 API 서버에서는 브라우저 세션 CSRF 방어가 필요하지 않으므로 비활성화합니다.
                .csrf(AbstractHttpConfigurer::disable)
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
}
