package HK.PrettyWorks_BE.auth.controller;

import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import HK.PrettyWorks_BE.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 임직원 정보를 받아 신규 계정을 생성합니다.
    @Operation(summary = "회원가입", description = "임직원 정보로 신규 계정 생성")
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 사번/비밀번호로 로그인하고 access/refresh 토큰을 발급합니다.
    @Operation(summary = "로그인", description = "사번과 비밀번호로 로그인 진행")
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<JwtTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtTokenResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    // refresh 토큰을 검증해 새 access/refresh 토큰 한 쌍을 발급합니다(RTR).
    @Operation(summary = "RTR", description = "Refresh Token 검증")
    @PostMapping("/api/v1/auth/reissue")
    public ResponseEntity<JwtTokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        JwtTokenResponse response = authService.reissue(request);

        return ResponseEntity.ok(response);
    }


}
