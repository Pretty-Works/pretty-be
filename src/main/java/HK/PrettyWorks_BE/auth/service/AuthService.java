package HK.PrettyWorks_BE.auth.service;

import HK.PrettyWorks_BE.auth.constant.AuthConstant;
import HK.PrettyWorks_BE.auth.constant.RevokeReason;
import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import HK.PrettyWorks_BE.auth.jwt.JwtUtil;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import HK.PrettyWorks_BE.auth.exception.AuthErrorCode;
import HK.PrettyWorks_BE.global.exception.BaseException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService
            refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public JwtTokenResponse login(LoginRequest request, String userAgent) {
        // 사번 미존재와 비밀번호 불일치 모두 동일한 에러로 응답하여 계정 존재 여부 노출을 막습니다.
        UserEntity user = userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> BaseException.type(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BaseException.type(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 퇴사자는 로그인 자체를 막습니다(휴직자는 복귀 준비·조회를 위해 허용).
        // 계정 상태가 노출되지 않도록 자격 증명 실패와 동일한 에러로 응답합니다.
        if (!UserPolicy.isEmployed(user)) {
            throw BaseException.type(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 기기별 세션을 새로 만들고, 그 세션 식별자(sid)를 토큰에 심습니다.
        String sessionId = refreshTokenService.createSession(user.getId());
        JwtTokenResponse tokens = jwtUtil.generateTokens(user.getId(), sessionId);
        refreshTokenService.save(sessionId, user.getId(), tokens.refreshToken(), userAgent);

        return tokens;
    }

    // 도난 탐지·퇴사 차단 분기에서 세션 폐기를 커밋해야 하므로 BaseException은 롤백 대상에서 제외합니다.
    @Transactional(noRollbackFor = BaseException.class)
    public JwtTokenResponse reissue(ReissueRequest request, String userAgent) {
        // 서명/만료/타입 검증을 먼저 통과한 토큰만 DB와 대조합니다.
        Claims claims = jwtUtil.getTokenBody(request.refreshToken());

        String tokenType = claims.get(AuthConstant.TOKEN_TYPE_CLAIM_NAME, String.class);
        if (!AuthConstant.REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw BaseException.type(GlobalErrorCode.INVALID_TOKEN_TYPE);
        }

        Long userId = JwtUtil.getUserId(claims);

        // 재발급 시점의 재직 상태를 다시 확인합니다. 로그인만 막으면 이미 발급된 refresh token으로
        // 계속 갱신할 수 있어 퇴사자 차단이 우회됩니다. 규칙은 로그인과 동일(휴직 허용, 퇴사 차단).
        // 회전보다 먼저 확인해, 어차피 끊을 요청에 새 토큰을 만들지 않습니다.
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (!UserPolicy.isEmployed(user)) {
            refreshTokenService.revokeSession(JwtUtil.getSessionId(claims), RevokeReason.NOT_EMPLOYED);
            throw BaseException.type(GlobalErrorCode.UNAUTHORIZED);
        }

        // 제시된 토큰을 폐기하고 이어서 쓸 세션을 받습니다.
        // 이미 폐기된 토큰이면(=누군가 옛 토큰을 들고 있음) 여기서 세션 전체가 무효화되고 예외가 납니다.
        String sessionId = refreshTokenService.rotate(request.refreshToken());

        // 같은 세션으로 새 토큰 쌍을 발급합니다. sid가 유지돼야 세션 추적·무효화가 이어집니다.
        JwtTokenResponse tokens = jwtUtil.generateTokens(userId, sessionId);
        refreshTokenService.save(sessionId, userId, tokens.refreshToken(), userAgent);

        return tokens;
    }

    // 로그아웃: 요청한 기기의 세션만 무효화합니다. 이미 끊긴 세션이어도 결과가 같아 멱등합니다.
    // 사유를 LOGOUT으로 남겨, 이후 옛 토큰이 다시 오더라도 도난 경보를 울리지 않고 거부만 합니다.
    @Transactional
    public void logout(String sessionId) {
        refreshTokenService.revokeSession(sessionId, RevokeReason.LOGOUT);
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
