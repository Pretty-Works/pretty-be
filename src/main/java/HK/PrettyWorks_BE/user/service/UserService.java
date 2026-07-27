package HK.PrettyWorks_BE.user.service;

import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

// 다른 사용자(호출자 본인이 아닌)를 다루는 공용 조회. 인증된 현재 사용자는 CurrentUserService가 담당한다.
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // userId → 이름 맵. 사람 이름을 함께 보여주는 조회에서 루프 안 개별 조회(N+1) 대신 한 번에 가져온다.
    // 이름은 항상 현재 값이므로 개명·부서 이동이 조회 결과에 바로 반영된다.
    @Transactional(readOnly = true)
    public Map<Long, String> getNameMap(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }
}
