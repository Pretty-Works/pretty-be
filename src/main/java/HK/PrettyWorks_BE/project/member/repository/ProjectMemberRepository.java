package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.project.member.constant.ProjectMemberStatus;
import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

    // 회의록·게시판 등에서 "이 사용자가 이 프로젝트의 참여중(ACTIVE) 멤버인가"를 인가할 때 사용합니다.
    boolean existsByProjectIdAndUserIdAndStatus(Long projectId, Long userId, ProjectMemberStatus status);
}
