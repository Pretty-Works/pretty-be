package HK.PrettyWorks_BE.project.member.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.member.exception.ProjectMemberErrorCode;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;

    // 이 사용자가 해당 프로젝트의 참여중(ACTIVE) 멤버인지 여부만 반환합니다.
    @Transactional(readOnly = true)
    public boolean isActiveMember(Long projectId, Long userId) {
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

    // 참여중(ACTIVE) 멤버십 행 자체를 반환합니다. 오너·역할(role)로 권한을 판정해야 할 때 사용합니다.
    // 없을 때 어떤 에러를 낼지는 도메인마다 다르므로(예: PROJECT_005 / PROJECT_017) Optional로 넘기고 호출자가 정합니다.
    @Transactional(readOnly = true)
    public Optional<ProjectMemberEntity> getActiveMembership(Long projectId, Long userId) {
        return projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, ProjectMemberStatus.ACTIVE);
    }
}
