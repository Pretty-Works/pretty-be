package HK.PrettyWorks_BE.project.member.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.exception.ProjectMemberErrorCode;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberResponse;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.policy.ProjectPolicy;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserSearchCondition;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    // 참여중(ACTIVE) 멤버인지 여부만 판정합니다.
    private boolean isActiveMember(Long projectId, Long userId) {
        return projectMemberRepository
                .existsByProjectIdAndUserIdAndStatus(projectId, userId, ProjectMemberStatus.ACTIVE);
    }

    // 참여중 멤버가 아니면 예외를 던집니다. 회의록·게시판 등에서 작성 전 인가 가드로 사용합니다.
    @Transactional(readOnly = true)
    public void validateActiveMember(Long projectId, Long userId) {
        if (!isActiveMember(projectId, userId)) {
            throw BaseException.type(ProjectMemberErrorCode.NOT_A_MEMBER);
        }
    }

    // 여러 명이 모두 참여중 멤버인지 한 번에 검증합니다. 회의록 참석자처럼 명단 전체를 봐야 할 때 사용합니다.
    // 한 명씩 validateActiveMember를 도는 것과 결과는 같지만 쿼리는 1번입니다.
    // 누가 아닌지는 알려주지 않습니다 — 명단 검증은 통과/거부만 필요하고, 개별 지목은 쿼리를 인원수만큼 되돌립니다.
    @Transactional(readOnly = true)
    public void validateActiveMembers(Long projectId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 중복이 있으면 count와 개수가 어긋나 멀쩡한 명단이 거부되므로 여기서 한 번 더 접습니다.
        Set<Long> distinctIds = new LinkedHashSet<>(userIds);

        long activeCount = projectMemberRepository
                .countByProjectIdAndUserIdInAndStatus(projectId, distinctIds, ProjectMemberStatus.ACTIVE);

        if (activeCount != distinctIds.size()) {
            throw BaseException.type(ProjectMemberErrorCode.NOT_A_MEMBER);
        }
    }

    // 프로젝트 존재(PROJECT_004) + 참여중 멤버(MEMBER_001)를 한 번에 검증합니다.
    // 할 일·지출·회의록 등 프로젝트 하위 도메인이 무엇을 하든 먼저 통과해야 하는 공통 관문입니다.
    //
    // 순서가 중요합니다. 멤버 검증을 먼저 하면 없는 projectId에 대해 "참여 기록 없음"이 되어
    // 404여야 할 응답이 403으로 나갑니다.
    //
    // 프로젝트를 로드하지 않고 존재만 확인하므로, 기간·상태 검증이 필요한 쓰기 API는
    // 이 메서드 대신 프로젝트를 직접 조회한 뒤 ProjectPolicy로 판정하세요.
    @Transactional(readOnly = true)
    public void validateAccess(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw BaseException.type(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        validateActiveMember(projectId, userId);
    }

    // 참여중(ACTIVE) 멤버십 행 자체를 반환합니다. 오너·역할(role)로 권한을 판정해야 할 때 사용합니다.
    // 없을 때 어떤 에러를 낼지는 도메인마다 다르므로(예: PROJECT_005 / PROJECT_017) Optional로 넘기고 호출자가 정합니다.
    @Transactional(readOnly = true)
    public Optional<ProjectMemberEntity> getActiveMembership(Long projectId, Long userId) {
        return projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, ProjectMemberStatus.ACTIVE);
    }

    // 알림 수신자 후보. 탈퇴 멤버(LEFT)는 여기서 빠지고, 행위자·퇴사자 제외는 NotificationPublisher가 한다.
    @Transactional(readOnly = true)
    public List<Long> getActiveMemberIds(Long projectId) {
        return projectMemberRepository
                .findByProjectIdAndStatusOrderByIdAsc(projectId, ProjectMemberStatus.ACTIVE).stream()
                .map(ProjectMemberEntity::getUserId)
                .toList();
    }

    // 오너의 userId. 오너 행은 항상 존재하지만, 없을 때의 처리는 호출자가 정하도록 Optional로 넘긴다.
    @Transactional(readOnly = true)
    public Optional<Long> getOwnerId(Long projectId) {
        return projectMemberRepository.findOwner(projectId).map(ProjectMemberEntity::getUserId);
    }

    /**
     * 알림 수신자용: 이 프로젝트를 관리하는 참여자(오너 또는 PM 부서) 전원.
     *
     * <p>회사 전체 PM이 아니라 <b>이 프로젝트에 참여중(ACTIVE)인</b> 사람만 나온다.
     * 탈퇴 멤버(LEFT)는 빠지고, 행위자·퇴사자 제외는 NotificationPublisher가 한다.
     *
     * <p>판정은 {@link ProjectPolicy#canUpdate}를 그대로 쓴다 — "프로젝트를 관리할 수 있는 사람이
     * 프로젝트 알림도 받는다". 같은 규칙을 JPQL 조건으로 다시 적으면 나중에 권한 기준이 바뀔 때
     * 한쪽만 고쳐져 "수정은 되는데 알림은 안 오는" 상태가 된다.
     * 참여자는 프로젝트당 한 자릿수라 전원을 읽어 거르는 비용은 문제되지 않는다.
     */
    @Transactional(readOnly = true)
    public List<Long> getManagerIds(Long projectId) {
        List<ProjectMemberEntity> members = projectMemberRepository
                .findByProjectIdAndStatusOrderByIdAsc(projectId, ProjectMemberStatus.ACTIVE);

        Map<Long, UserEntity> users = userService.getUserMap(
                members.stream().map(ProjectMemberEntity::getUserId).toList());

        return members.stream()
                .filter(member -> {
                    UserEntity user = users.get(member.getUserId());
                    return user != null && ProjectPolicy.canUpdate(member, user);
                })
                .map(ProjectMemberEntity::getUserId)
                .toList();
    }

    /**
     * 참여중 재직자 조회. 명단 화면·참여자 추가 자동완성·에이전트 도구가 모두 이 진입점을 쓴다.
     *
     * <p>휴직자도 포함한다 — 프로젝트 참여·회의 참석이 열려 있으므로 사내 임직원 검색과 기준이 같아야 한다.
     * 세 용도가 다른 명단을 받으면 화면에는 보이는 사람이 에이전트에게는 안 보이는 식으로 갈린다.
     *
     * @param keyword     이름 부분 일치. 비우면 전체
     * @param excludeSelf 요청자 본인을 뺄지. 참여자 추가 자동완성은 이미 참여 중인 본인을 후보로 둘 이유가 없다
     * @param size        1~100. 자동완성 상한(20)보다 큰 이유는 명단이 전원을 받아야 하기 때문이다
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long projectId, Long requesterId,
                                                  String keyword, boolean excludeSelf, int size) {
        currentUserService.getEmployedUser(requesterId);
        validateAccess(projectId, requesterId);

        return projectMemberRepository.findActiveMembers(
                        projectId,
                        UserSearchCondition.normalizeKeyword(keyword),
                        excludeSelf ? requesterId : null,
                        ProjectMemberStatus.ACTIVE, UserPolicy.EMPLOYED_STATUSES,
                        PageRequests.of(0, size))
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }
}
