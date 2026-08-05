package HK.PrettyWorks_BE.project.member.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.exception.ProjectMemberErrorCode;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberSearchResponse;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import HK.PrettyWorks_BE.project.project.exception.ProjectErrorCode;
import HK.PrettyWorks_BE.project.project.repository.ProjectRepository;
import HK.PrettyWorks_BE.user.policy.UserPolicy;
import HK.PrettyWorks_BE.user.service.CurrentUserService;
import HK.PrettyWorks_BE.user.service.UserSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

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

    // 회의록·할 일 등 프로젝트 하위 기능에서 사용할 참여중 재직자 이름 자동완성.
    // 휴직자도 포함한다 — 프로젝트 참여·회의 참석이 열려 있으므로 사내 임직원 검색과 기준이 같아야 한다.
    @Transactional(readOnly = true)
    public List<ProjectMemberSearchResponse> searchMembers(Long projectId, Long requesterId,
                                                           String keyword, int limit) {
        currentUserService.getEmployedUser(requesterId);
        validateAccess(projectId, requesterId);

        UserSearchCondition condition = UserSearchCondition.of(keyword, limit);
        if (condition.isEmpty()) {
            return List.of();
        }

        return projectMemberRepository.searchActiveMembers(
                        projectId, requesterId, condition.keyword(),
                        ProjectMemberStatus.ACTIVE, UserPolicy.EMPLOYED_STATUSES,
                        PageRequest.of(0, condition.limit()))
                .stream()
                .map(ProjectMemberSearchResponse::from)
                .toList();
    }
}
