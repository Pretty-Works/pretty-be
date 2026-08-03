package HK.PrettyWorks_BE.auth.controller;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import HK.PrettyWorks_BE.auth.jwt.TokenPair;
import HK.PrettyWorks_BE.auth.service.AuthService;
import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.security.info.UserAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    // 로컬(http)에서는 false, 배포 환경(https)에서는 환경변수(AUTH_COOKIE_SECURE)로 true를 덮어씁니다.
    // application.yml은 .gitignore 대상이라 팀원 로컬에는 이 키가 없을 수 있어, 기본값을 둬서 부팅이 깨지지 않게 합니다.
    @Value("${auth.cookie.secure:false}")
    private boolean cookieSecure;

    // 임직원 정보를 받아 신규 계정을 생성합니다.
    @Override
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 사번/비밀번호로 로그인합니다. accessToken은 바디로, refreshToken은 HttpOnly 쿠키로 내려줍니다.
    // 기기별로 세션이 따로 만들어집니다.
    @Override
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<JwtTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            // 브라우저·앱이 자동으로 붙이는 헤더입니다. 로그인 기기 표시용으로만 보관합니다.
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        TokenPair tokens = authService.login(request, userAgent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueRefreshTokenCookie(tokens.refreshToken()).toString())
                .body(toResponse(tokens));
    }

    // refreshToken 쿠키를 검증해 새 access/refresh 토큰 한 쌍을 발급합니다(RTR). 세션은 그대로 유지됩니다.
    @Override
    @PostMapping("/api/v1/auth/reissue")
    public ResponseEntity<JwtTokenResponse> reissue(
            @CookieValue(value = AuthConstant.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        // 쿠키 자체가 없으면(만료·미로그인) 서비스까지 가지 않고 바로 거부합니다.
        if (refreshToken == null) {
            throw BaseException.type(GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        TokenPair tokens = authService.reissue(refreshToken, userAgent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueRefreshTokenCookie(tokens.refreshToken()).toString())
                .body(toResponse(tokens));
    }

    // 요청한 기기의 세션만 종료하고, refreshToken 쿠키도 함께 지웁니다.
    @Override
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        // 세션 식별자가 필요해 principal 대신 인증 객체를 직접 받습니다. (다른 API는 @AuthenticationPrincipal 그대로)
        String sessionId = ((UserAuthentication) authentication).getSessionId();
        authService.logout(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
                .build();
    }

    private JwtTokenResponse toResponse(TokenPair tokens) {
        return JwtTokenResponse.builder()
                .accessToken(tokens.accessToken())
                .build();
    }

    // 발급·삭제 쿠키의 공통 속성을 한 곳에서 만듭니다. 브라우저는 이름·path가 같아야 같은 쿠키로 보고 덮어쓰므로,
    // 두 쪽이 따로 관리되면 규칙이 어긋났을 때 로그아웃이 쿠키를 지우지 못합니다.
    private ResponseCookie.ResponseCookieBuilder refreshTokenCookie(String value) {
        return ResponseCookie.from(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(AuthConstant.REFRESH_TOKEN_COOKIE_PATH);
    }

    // 로그인·재발급에서 새로 발급한 refreshToken을 HttpOnly 쿠키로 감쌉니다.
    private ResponseCookie issueRefreshTokenCookie(String refreshToken) {
        return refreshTokenCookie(refreshToken)
                .maxAge(Duration.ofMillis(jwtUtil.getRefreshTokenExpirePeriod()))
                .build();
    }

    // 로그아웃에서 즉시 만료되는(maxAge=0) 빈 쿠키를 덮어써 브라우저에서 지웁니다.
    private ResponseCookie deleteRefreshTokenCookie() {
        return refreshTokenCookie("")
                .maxAge(0)
                .build();
    }
}
