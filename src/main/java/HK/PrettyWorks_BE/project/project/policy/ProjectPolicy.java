package HK.PrettyWorks_BE.project.project.policy;

import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

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

    // 수정 권한: 오너이거나 부서가 PM. role은 표시 전용이라 판정에 쓰지 않습니다.
    // 참여중(ACTIVE) 여부는 호출부의 멤버십 조회가 이미 걸러냅니다.
    public static boolean canUpdate(ProjectMemberEntity callerMembership, UserEntity caller) {
        return callerMembership.isOwner()
                || caller.getDepartment() == DepartmentType.PM;
    }

    // 상태 변경도 canUpdate를 그대로 씁니다. 전용 메서드를 따로 두었더니 본문이 같은데도
    // 규칙이 다른 것처럼 읽혀, 실제로 "상태 변경은 오너만"이라는 오해가 주석과 문서에 남았습니다.
    // 오너 단독으로 두지 않는 이유: 오너가 퇴사하면 아무도 완료·보관 처리를 못 해 프로젝트가 영구히 묶입니다.

    // 날짜가 프로젝트 기간(startDate ~ targetDate) 안인지 판정합니다. 양끝(시작일·목표일 당일) 포함.
    // 할 일 마감일·회의 일자·지출 사용일 등 "프로젝트 소속 날짜"의 공통 검증 규칙입니다.
    public static boolean isWithinPeriod(ProjectEntity project, LocalDate date) {
        return isWithinPeriod(project.getStartDate(), project.getTargetDate(), date);
    }

    // 기간을 값으로 받는 형태. 아직 저장되지 않은 요청값(생성·수정 중인 프로젝트 기간)으로 검증할 때 사용합니다.
    public static boolean isWithinPeriod(LocalDate startDate, LocalDate targetDate, LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(targetDate);
    }

    // 프로젝트가 콘텐츠(회의록·게시글 등) 추가/수정을 받을 수 있는 상태인지 확인합니다.
    // 완료·보관이면 닫힌 것으로 봅니다.
    public static boolean isOpenForContent(ProjectEntity project) {
        return isOpenForContent(project.getStatus());
    }

    // 상태 값만 가진 쪽(조회 응답으로 받은 경우)을 위한 형태. 엔티티를 다시 읽지 않으려고 둡니다.
    // 판정 규칙은 위 메서드와 같아야 하므로 한 곳에 둡니다.
    public static boolean isOpenForContent(ProjectStatus status) {
        return status != ProjectStatus.COMPLETED && status != ProjectStatus.ARCHIVED;
    }

    // 마일스톤이 비어 있는지(입력 자체가 없는지) 판정합니다. 화면에서 빈 줄이 딸려오는 경우라 무시 대상입니다.
    public static boolean isEmptyMilestone(LocalDate targetDate, String goal) {
        return targetDate == null && !StringUtils.hasText(goal);
    }

    // 마일스톤이 저장 가능한 형태인지 판정합니다. 목표일과 목표 내용은 한쪽만 있을 수 없습니다.
    // isEmptyMilestone과 같은 두 필드를 보므로, 규칙이 바뀌면 이 두 메서드를 함께 고쳐야 합니다.
    public static boolean isCompleteMilestone(LocalDate targetDate, String goal) {
        return targetDate != null && StringUtils.hasText(goal);
    }

    // 상태 전이 규칙: 보관(ARCHIVED)은 완전 종료라 어디로도 못 가고, 완료(COMPLETED)는 보관으로만 갈 수 있습니다.
    // 그 외 진행 상태끼리는 자유롭게 전이됩니다. (되돌림 차단 = PROJECT_019)
    public static boolean isAllowedTransition(ProjectStatus current, ProjectStatus target) {
        if (current == ProjectStatus.ARCHIVED) {
            return false;
        }
        if (current == ProjectStatus.COMPLETED) {
            return target == ProjectStatus.ARCHIVED;
        }
        return true;
    }

}
