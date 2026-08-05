package HK.PrettyWorks_BE.project.member.repository;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;

// 프로젝트 멤버 자동완성 조회 전용 projection.
public interface ProjectMemberSearchRow {
    Long getUserId();

    String getName();

    DepartmentType getDepartment();

    PositionType getPosition();

    StatusType getStatus();

    String getRole();
}
