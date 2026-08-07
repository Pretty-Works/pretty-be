package HK.PrettyWorks_BE.user.dto.res;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import lombok.Builder;

// 내 정보 조회 응답. 본인 토큰으로만 부르는 경로라 사번·메일은 함께 준다.
// 나머지 개인정보(phoneNumber·birthDate 등)는 화면이 쓰지 않아 넣지 않는다.
@Builder
public record MyProfileResponse(
        Long userId,
        String name,
        // 사번·메일은 프로필 메뉴에 표시한다
        String employeeNo,
        String email,
        DepartmentType department,
        PositionType position,
        // 판정에 쓰는 직급 서열(PositionType.level)이 서버에만 있어 결과만 내려준다.
        boolean canCreateProject
) {
    public static MyProfileResponse of(UserEntity user, boolean canCreateProject) {
        return MyProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .employeeNo(user.getEmployeeNo())
                .email(user.getEmail())
                .department(user.getDepartment())
                .position(user.getPosition())
                .canCreateProject(canCreateProject)
                .build();
    }
}
