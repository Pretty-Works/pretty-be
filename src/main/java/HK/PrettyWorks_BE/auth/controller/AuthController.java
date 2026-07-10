package HK.PrettyWorks_BE.auth.controller;

import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 사번/비밀번호로 로그인하고 access/refresh 토큰을 발급합니다.
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<JwtTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtTokenResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    // refresh 토큰을 검증해 새 access/refresh 토큰 한 쌍을 발급합니다(RTR).
    @PostMapping("/api/v1/auth/reissue")
    public ResponseEntity<JwtTokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        JwtTokenResponse response = authService.reissue(request);

        return ResponseEntity.ok(response);
    }


}
