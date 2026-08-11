package HK.PrettyWorks_BE.user.service;

import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.dto.res.MyProfileResponse;
import HK.PrettyWorks_BE.user.dto.res.UserSearchResponse;
import HK.PrettyWorks_BE.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 다른 사용자(호출자 본인이 아닌)를 다루는 공용 조회. 인증된 현재 사용자는 CurrentUserService가 담당한다.
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    // 내 정보 조회. 토큰의 userId로만 조회하므로 남의 정보에 접근할 경로가 없다.
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        UserEntity user = currentUserService.getCurrentUser(userId);

        return MyProfileResponse.of(user, ProjectPolicy.canCreate(user));
    }

    // 프로젝트·일정의 사용자 선택 드롭다운용 이름 자동완성.
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(Long requesterId, String keyword, int limit) {
        currentUserService.getEmployedUser(requesterId);

        UserSearchCondition condition = UserSearchCondition.of(keyword, limit);
        if (condition.isEmpty()) {
            return List.of();
        }

        return userRepository.searchByName(
                        requesterId, condition.keyword(), UserPolicy.EMPLOYED_STATUSES,
                        PageRequest.of(0, condition.limit()))
                .stream()
                .map(UserSearchResponse::from)
                .toList();
    }

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

    // 이름 말고 부서·직급까지 필요한 화면용(프로젝트 상세의 참여자 카드 등).
    // getNameMap과 나눠 둔 이유: 이름만 쓰는 쪽이 훨씬 많은데 전부 엔티티를 들고 다니면
    // 호출부가 필요 이상으로 많은 필드에 접근할 수 있게 된다.
    @Transactional(readOnly = true)
    public Map<Long, UserEntity> getUserMap(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));
    }
}
