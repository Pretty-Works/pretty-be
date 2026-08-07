package HK.PrettyWorks_BE.project.member.dto.res;

import HK.PrettyWorks_BE.project.member.repository.ProjectMemberRow;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;
import lombok.Builder;

// 프로젝트 참여자 한 명. 명단 조회와 참여자 추가 자동완성이 함께 쓴다.
//
// 리포지토리 projection(ProjectMemberRow)을 서비스 밖으로 그대로 내보내지 않기 위한 경계다.
// 쿼리를 바꾸면 projection이 바뀌는데, 그게 호출자까지 깨뜨리면 안 된다.
@Builder
public record ProjectMemberResponse(
        Long userId,
        String name,
        DepartmentType department,
        PositionType position,
        StatusType status,
        String role,
        boolean isOwner
) {
    public static ProjectMemberResponse from(ProjectMemberRow row) {
        return ProjectMemberResponse.builder()
                .userId(row.getUserId())
                .name(row.getName())
                .department(row.getDepartment())
                .position(row.getPosition())
                .status(row.getStatus())
                .role(row.getRole())
                .isOwner(row.getIsOwner())
                .build();
    }
}
