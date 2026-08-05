package HK.PrettyWorks_BE.project.member.dto.res;

import HK.PrettyWorks_BE.project.member.repository.ProjectMemberSearchRow;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;
import lombok.Builder;

// 프로젝트 하위 기능에서 참여자를 선택할 때 사용하는 자동완성 응답.
@Builder
public record ProjectMemberSearchResponse(
        Long userId,
        String name,
        DepartmentType department,
        PositionType position,
        StatusType status,
        String role
) {
    public static ProjectMemberSearchResponse from(ProjectMemberSearchRow row) {
        return ProjectMemberSearchResponse.builder()
                .userId(row.getUserId())
                .name(row.getName())
                .department(row.getDepartment())
                .position(row.getPosition())
                .status(row.getStatus())
                .role(row.getRole())
                .build();
    }
}
