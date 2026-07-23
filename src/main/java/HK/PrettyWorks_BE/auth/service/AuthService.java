package HK.PrettyWorks_BE.auth.service;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.auth.domain.RefreshTokenEntity;
import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import HK.PrettyWorks_BE.auth.repository.RefreshTokenRepository;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import HK.PrettyWorks_BE.auth.exception.AuthErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public JwtTokenResponse login(LoginRequest request) {
        // 사번 미존재와 비밀번호 불일치 모두 동일한 에러로 응답하여 계정 존재 여부 노출을 막습니다.
        UserEntity user = userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> BaseException.type(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BaseException.type(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokensAndPersist(user.getId());

    }

    // 로그인/재발급 양쪽에서 동일한 방식으로 토큰을 발급하고 RTR 정책에 따라 DB를 upsert합니다.
    private JwtTokenResponse issueTokensAndPersist(Long userId) {
        JwtTokenResponse tokens = jwtUtil.generateTokens(userId);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(jwtUtil.getRefreshTokenExpirePeriod(), ChronoUnit.MILLIS);

        RefreshTokenEntity refreshToken = refreshTokenRepository.findById(userId)
                .map(existing -> {
                    existing.update(tokens.refreshToken(), expiresAt);
                    return existing;
                })
                .orElseGet(() -> RefreshTokenEntity.builder()
                        .userId(userId)
                        .token(tokens.refreshToken())
                        .expiresAt(expiresAt)
                        .build());
        refreshTokenRepository.save(refreshToken);

        return tokens;
    }

    // 도난 의심 분기에서 deleteById를 커밋해야 하므로 BaseException은 롤백 대상에서 제외합니다.
    @Transactional(noRollbackFor = BaseException.class)
    public JwtTokenResponse reissue(ReissueRequest request) {
        // 서명/만료/타입 검증을 먼저 통과한 토큰만 DB와 비교합니다.
        Claims claims = jwtUtil.getTokenBody(request.refreshToken());

        String tokenType = claims.get(AuthConstant.TOKEN_TYPE_CLAIM_NAME, String.class);
        if (!AuthConstant.REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw BaseException.type(GlobalErrorCode.INVALID_TOKEN_TYPE);
        }

        Long userId = claims.get(AuthConstant.USER_ID_CLAIM_NAME, Long.class);
        if (userId == null) {
            throw BaseException.type(GlobalErrorCode.INVALID_JWT);
        }

        RefreshTokenEntity stored = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // DB의 토큰과 다르다면 누군가 이미 재발급을 받은 상황이라 도난 의심으로 보고 강제 로그아웃 처리합니다.
        if (!stored.getToken().equals(request.refreshToken())) {
            refreshTokenRepository.deleteById(userId);
            throw BaseException.type(GlobalErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        return issueTokensAndPersist(userId);
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 사번/이메일은 unique 제약이라 저장 전에 중복을 먼저 걸러 명확한 에러로 응답합니다.
        if (userRepository.existsByEmployeeNo(request.employeeNo())) {
            throw BaseException.type(AuthErrorCode.EMPLOYEE_NO_DUPLICATED);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw BaseException.type(AuthErrorCode.EMAIL_DUPLICATED);
        }

        // 원문 비밀번호는 저장하지 않고 BCrypt 해시만 보관합니다. 신규 가입자는 재직중(ACTIVE)으로 시작합니다.
        UserEntity user = UserEntity.builder()
                .employeeNo(request.employeeNo())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .birthDate(request.birthDate())
                .gender(request.gender())
                .department(request.department())
                .position(request.position())
                .status(StatusType.ACTIVE)
                .hireDate(request.hireDate())
                .build();

        UserEntity saved = userRepository.save(user);

        return SignupResponse.builder()
                .userId(saved.getId())
                .build();
    }
}
