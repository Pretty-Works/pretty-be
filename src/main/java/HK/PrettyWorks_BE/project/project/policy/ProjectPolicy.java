package HK.PrettyWorks_BE.project.project.policy;

import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.domain.UserEntity;

// 프로젝트 관련 권한 규칙을 한 곳에 모은 정책 클래스.
// 규칙이 바뀌면 이 클래스만 수정하면 되고, 서비스는 흐름(오케스트레이션)에만 집중합니다.
public final class ProjectPolicy {

    // 프로젝트 내 직무 역할 중 수정 권한을 가지는 값 (role은 자유 문자열이라 문자열로 비교).
    private static final String ROLE_PM = "PM";

    private ProjectPolicy() {
    }

    // 생성 권한: 직급이 팀장 이상이거나, 부서가 PM인 사용자만 프로젝트를 생성할 수 있습니다.
    public static boolean canCreate(UserEntity user) {
        return user.getPosition().getLevel() >= PositionType.TEAM_LEADER.getLevel()
                || user.getDepartment() == DepartmentType.PM;
    }

    // 수정 권한: 회사 직급과 무관하게 "그 프로젝트 안에서의 지위"로 판정합니다.
    // 대상 프로젝트의 오너(is_owner=true)이거나, 프로젝트 내 직무 역할이 PM인 참여자만 수정할 수 있습니다.
    // callerMembership: 호출자의 해당 프로젝트 멤버십 행 (DB 조회는 서비스가 담당, 여기선 판정만).
    public static boolean canUpdate(ProjectMemberEntity callerMembership) {
        return callerMembership.isOwner()
                || ROLE_PM.equals(callerMembership.getRole());
    }

    // 참고 — 향후 삭제 API에서 추가할 권한 규칙:
    //  - 삭제(canDelete) : 생성자 본인(오너)만
}
