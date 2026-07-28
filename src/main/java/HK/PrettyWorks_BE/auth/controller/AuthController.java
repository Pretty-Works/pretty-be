package HK.PrettyWorks_BE.auth.controller;

import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import HK.PrettyWorks_BE.auth.service.AuthService;
import HK.PrettyWorks_BE.security.info.UserAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    // 임직원 정보를 받아 신규 계정을 생성합니다.
    @Override
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 사번/비밀번호로 로그인하고 access/refresh 토큰을 발급합니다. 기기별로 세션이 따로 만들어집니다.
    @Override
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<JwtTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            // 브라우저·앱이 자동으로 붙이는 헤더입니다. 로그인 기기 표시용으로만 보관합니다.
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        JwtTokenResponse response = authService.login(request, userAgent);

        return ResponseEntity.ok(response);
    }

    // refresh 토큰을 검증해 새 access/refresh 토큰 한 쌍을 발급합니다(RTR). 세션은 그대로 유지됩니다.
    @Override
    @PostMapping("/api/v1/auth/reissue")
    public ResponseEntity<JwtTokenResponse> reissue(
            @Valid @RequestBody ReissueRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        JwtTokenResponse response = authService.reissue(request, userAgent);

        return ResponseEntity.ok(response);
    }

    // 요청한 기기의 세션만 종료합니다. 다른 기기의 로그인은 유지됩니다.
    @Override
    @PostMapping("/api/v1/auth/logout")
    public Void logout(Authentication authentication) {
        // 세션 식별자가 필요해 principal 대신 인증 객체를 직접 받습니다. (다른 API는 @AuthenticationPrincipal 그대로)
        String sessionId = ((UserAuthentication) authentication).getSessionId();
        authService.logout(sessionId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }
}
