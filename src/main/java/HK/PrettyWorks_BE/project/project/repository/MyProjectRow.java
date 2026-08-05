package HK.PrettyWorks_BE.project.project.repository;

import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;

// 내 프로젝트 목록 한 행: 프로젝트 + 그 프로젝트에서의 내 멤버십.
//
// 조회 조건이 이미 "내가 참여중인 프로젝트"라 멤버 행이 조인돼 있다. 그걸 버리고 나중에
// 역할·오너 여부를 다시 조회하면 프로젝트 수만큼 쿼리가 더 나간다(N+1).
public record MyProjectRow(
        ProjectEntity project,
        ProjectMemberEntity membership
) {
}
