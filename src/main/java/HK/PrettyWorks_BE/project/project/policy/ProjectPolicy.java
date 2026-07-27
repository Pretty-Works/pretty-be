package HK.PrettyWorks_BE.project.project.policy;

import HK.PrettyWorks_BE.project.member.domain.ProjectMemberEntity;
import HK.PrettyWorks_BE.project.project.constant.ProjectStatus;
import HK.PrettyWorks_BE.project.project.domain.ProjectEntity;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.domain.UserEntity;

import java.time.LocalDate;

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

    // 상태 변경 권한: 오너(생성자)만 프로젝트 상태를 변경할 수 있습니다. (수정 권한과 달리 PM은 불가)
    public static boolean canChangeStatus(ProjectMemberEntity callerMembership) {
        return callerMembership.isOwner();
    }

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
        ProjectStatus status = project.getStatus();
        return status != ProjectStatus.COMPLETED && status != ProjectStatus.ARCHIVED;
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
