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

    public JwtTokenResponse generateTokens(final Long userId) {
        return JwtTokenResponse.builder()
                .accessToken(generateToken(userId, accessTokenExpirePeriod, AuthConstant.ACCESS_TOKEN_TYPE))
                .refreshToken(generateToken(userId, refreshTokenExpirePeriod, AuthConstant.REFRESH_TOKEN_TYPE))
                .build();
    }

    private String generateToken(
            final Long userId,
            final Long expirePeriod,
            final String tokenType
    ) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .header()
                .type(Header.JWT_TYPE)
                .and()
                .claim(AuthConstant.USER_ID_CLAIM_NAME, userId)
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

    public Long getUserIdFromToken(final String token) {
        Claims claims = getTokenBody(token);
        return claims.get(AuthConstant.USER_ID_CLAIM_NAME, Long.class);
    }

    public static Object checkPrincipal(final Object principal) {
        if (AuthConstant.ANONYMOUS_USER.equals(principal)) {
            throw new BaseException(GlobalErrorCode.UNAUTHORIZED);
        }

        return principal;
    }
}