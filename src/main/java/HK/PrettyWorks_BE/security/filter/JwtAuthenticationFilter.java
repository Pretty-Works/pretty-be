package HK.PrettyWorks_BE.security.filter;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.security.info.UserAuthentication;
import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 토큰 생성/검증 로직은 JwtUtil에 위임하고, 이 필터는 SecurityContext 설정만 담당합니다.
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // Authorization 헤더에서 Bearer 토큰만 추출합니다.
        String token = resolveToken(request);

        // 토큰이 없는 요청은 인증 객체를 만들지 않고 다음 필터로 넘깁니다.
        if (StringUtils.hasText(token)) {
            // JwtUtil이 서명/만료 검증을 마치고 토큰 본문(claims)을 반환합니다.
            Claims claims = jwtUtil.getTokenBody(token);

            // refresh token으로 API에 접근하지 못하도록 access token만 허용합니다.
            String tokenType = claims.get(AuthConstant.TOKEN_TYPE_CLAIM_NAME, String.class);

            if (!AuthConstant.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                throw new BaseException(GlobalErrorCode.INVALID_TOKEN_TYPE);
            }

            // 인증 주체로 사용할 userId를 토큰에서 꺼냅니다.
            Long userId = claims.get(AuthConstant.USER_ID_CLAIM_NAME, Long.class);
            if (userId == null) {
                throw new BaseException(GlobalErrorCode.INVALID_JWT);
            }

            // Spring Security가 이해하는 인증 객체를 만들어 현재 요청의 인증 상태로 등록합니다.
            UserAuthentication authentication = UserAuthentication.createUserAuthentication(userId);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 이 필터의 일이 끝났으므로 다음 필터 또는 컨트롤러로 요청을 넘깁니다.
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // JWT는 Authorization: Bearer {token} 형식으로 받습니다.
        String bearerToken = request.getHeader(AuthConstant.AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(AuthConstant.BEARER_PREFIX)) {
            // "Bearer " 접두어를 제거하고 순수 토큰 문자열만 반환합니다.
            return bearerToken.substring(AuthConstant.BEARER_PREFIX.length());
        }

        // 헤더가 없거나 형식이 다르면 인증을 시도하지 않습니다.
        return null;
    }
}
