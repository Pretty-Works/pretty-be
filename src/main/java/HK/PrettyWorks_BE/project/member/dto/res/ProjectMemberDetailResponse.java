package HK.PrettyWorks_BE.project.member.dto.res;

import HK.PrettyWorks_BE.project.member.repository.ProjectMemberDetailRow;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import lombok.Builder;

// 참여자 한 명의 전체 정보. 자동완성용 ProjectMemberSearchResponse와 달리 오너 여부를 포함하고
// 요청자 본인도 결과에 남는다 — 권한 판정과 "나를 제외한 명단" 구성에 둘 다 필요하다.
//
// 리포지토리 프로젝션(ProjectMemberDetailRow)을 서비스 밖으로 그대로 내보내지 않기 위한 경계다.
// 쿼리를 바꾸면 프로젝션이 바뀌는데, 그게 호출자까지 깨뜨리면 안 된다.
@Builder
public record ProjectMemberDetailResponse(
        Long userId,
        String name,
        DepartmentType department,
        PositionType position,
        String role,
        boolean isOwner
) {
    public static ProjectMemberDetailResponse from(ProjectMemberDetailRow row) {
        return ProjectMemberDetailResponse.builder()
                .userId(row.getUserId())
                .name(row.getName())
                .department(row.getDepartment())
                .position(row.getPosition())
                .role(row.getRole())
                .isOwner(row.getIsOwner())
                .build();
    }
}
