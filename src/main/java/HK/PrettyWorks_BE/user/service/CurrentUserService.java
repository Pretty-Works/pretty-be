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
}
