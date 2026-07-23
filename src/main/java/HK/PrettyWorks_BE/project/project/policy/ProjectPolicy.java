package HK.PrettyWorks_BE.project.project.policy;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.domain.UserEntity;

// 프로젝트 관련 권한 규칙을 한 곳에 모은 정책 클래스.
// 규칙이 바뀌면 이 클래스만 수정하면 되고, 서비스는 흐름(오케스트레이션)에만 집중합니다.
public final class ProjectPolicy {

    private ProjectPolicy() {
    }

    // 생성 권한: 직급이 팀장 이상이거나, 부서가 PM인 사용자만 프로젝트를 생성할 수 있습니다.
    public static boolean canCreate(UserEntity user) {
        return user.getPosition().getLevel() >= PositionType.TEAM_LEADER.getLevel()
                || user.getDepartment() == DepartmentType.PM;
    }

    // 참고 — 향후 API에서 추가할 권한 규칙 (구현 시 아래 메서드를 여기에 정의):
    //  - 수정(canUpdate(user, ownerId)) : 생성자 본인(오너)이거나 부서가 PM
    //  - 삭제(canDelete(user, ownerId)) : 생성자 본인(오너)만
}
