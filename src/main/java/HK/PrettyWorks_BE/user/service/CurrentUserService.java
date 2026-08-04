package HK.PrettyWorks_BE.user.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.global.exception.GlobalErrorCode;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.exception.UserErrorCode;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 토큰의 userId로 현재(인증된) 사용자를 로드하는 공용 서비스.
// "현재 유저 로드 + (선택) 활성 검증"의 단일 진입점 — 각 도메인 서비스가 재사용한다.
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    // 토큰 userId로 현재 유저 로드. 토큰은 유효한데 유저가 없으면 인증을 신뢰할 수 없어 UNAUTHORIZED.
    @Transactional(readOnly = true)
    public UserEntity getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
    }

    // 현재 유저 로드 + 활성(재직중) 검증 (USER_001). "활성" 규칙은 UserPolicy가 소유.
    @Transactional(readOnly = true)
    public UserEntity getActiveUser(Long userId) {
        UserEntity user = getCurrentUser(userId);
        if (!UserPolicy.isActive(user)) {
            throw BaseException.type(UserErrorCode.INACTIVE_USER);
        }
        return user;
    }

    // 현재 유저 로드 + 재직 검증 (USER_003). 휴직(ON_LEAVE)은 통과, 퇴사(RESIGNED)만 거부.
    //
    // 퇴사자는 로그인·재발급이 이미 막혀 있지만 발급된 access token이 만료까지 남아 있어,
    // 퇴사 처리 직후 그 시간만큼 창이 열린다. 이 검증이 그 창을 닫는다.
    // 휴직자까지 막아야 하는 행위(업무 수행)라면 getActiveUser를 쓴다.
    @Transactional(readOnly = true)
    public UserEntity getEmployedUser(Long userId) {
        UserEntity user = getCurrentUser(userId);
        if (!UserPolicy.isEmployed(user)) {
            throw BaseException.type(UserErrorCode.RESIGNED_USER);
        }
        return user;
    }

    // 에이전트 Run 생성은 사용자별 동시 실행 수를 같은 트랜잭션에서 직렬화해야 한다.
    // 검증 규칙은 getEmployedUser와 같되, 조회 시점부터 사용자 행을 잠가 이후 count 검사가 경쟁하지 않게 한다.
    @Transactional
    public UserEntity getEmployedUserForUpdate(Long userId) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> BaseException.type(GlobalErrorCode.UNAUTHORIZED));
        if (!UserPolicy.isEmployed(user)) {
            throw BaseException.type(UserErrorCode.RESIGNED_USER);
        }
        return user;
    }
}
