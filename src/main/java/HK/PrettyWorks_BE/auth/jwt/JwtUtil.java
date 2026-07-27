package HK.PrettyWorks_BE.auth.jwt;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil implements InitializingBean {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.access-token-expire-period}")
    @Getter
    private Long accessTokenExpirePeriod;

    @Value("${jwt.refresh-token-expire-period}")
    @Getter
    private Long refreshTokenExpirePeriod;

    private SecretKey key;

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // access·refresh에 같은 sessionId를 심습니다. 재발급으로 토큰이 회전해도 세션은 유지되므로,
    // 세션 하나를 무효화하면 그 세션에서 나온 모든 토큰이 함께 죽습니다.
    public JwtTokenResponse generateTokens(final Long userId, final String sessionId) {
        return JwtTokenResponse.builder()
                .accessToken(generateToken(userId, sessionId, accessTokenExpirePeriod, AuthConstant.ACCESS_TOKEN_TYPE))
                .refreshToken(generateToken(userId, sessionId, refreshTokenExpirePeriod, AuthConstant.REFRESH_TOKEN_TYPE))
                .build();
    }

    private String generateToken(
            final Long userId,
            final String sessionId,
            final Long expirePeriod,
            final String tokenType
    ) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .header()
                .type(Header.JWT_TYPE)
                .and()
                .claim(AuthConstant.USER_ID_CLAIM_NAME, userId)
                .claim(AuthConstant.SESSION_ID_CLAIM_NAME, sessionId)
                .claim(AuthConstant.TOKEN_TYPE_CLAIM_NAME, tokenType)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirePeriod))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public Claims getTokenBody(final String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BaseException(GlobalErrorCode.INVALID_JWT);
        }
    }

    // 파싱된 토큰 본문에서 인증 주체(userId)를 꺼냅니다. 클레임이 비어 있으면 신뢰할 수 없는 토큰입니다.
    // (토큰을 두 번 파싱하지 않도록, 이미 얻은 Claims를 받습니다)
    public static Long getUserId(final Claims claims) {
        Long userId = claims.get(AuthConstant.USER_ID_CLAIM_NAME, Long.class);
        if (userId == null) {
            throw new BaseException(GlobalErrorCode.INVALID_JWT);
        }

        return userId;
    }

    // 세션 식별자(sid)를 꺼냅니다. sid가 없으면 세션 무효화(로그아웃·도난 차단)를 적용할 수 없으므로
    // 통과시키지 않고 거부합니다. sid 도입 이전에 발급된 토큰도 여기서 걸립니다.
    public static String getSessionId(final Claims claims) {
        String sessionId = claims.get(AuthConstant.SESSION_ID_CLAIM_NAME, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new BaseException(GlobalErrorCode.INVALID_JWT);
        }

        return sessionId;
    }
}