package HK.PrettyWorks_BE.user.dto.res;

import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;
import lombok.Builder;

// 사용자 선택 자동완성 응답. 선택과 동명이인 구분에 필요한 정보만 노출한다.
@Builder
public record UserSearchResponse(
        Long userId,
        String name,
        DepartmentType department,
        PositionType position,
        StatusType status
) {
    public static UserSearchResponse from(UserEntity user) {
        return UserSearchResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .status(user.getStatus())
                .build();
    }
}
