package HK.PrettyWorks_BE.project.member.service;

import HK.PrettyWorks_BE.global.exception.BaseException;
import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.exception.ProjectMemberErrorCode;
import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
