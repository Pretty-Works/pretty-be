package HK.PrettyWorks_BE.user.policy;

import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;

// 사용자 관련 규칙. 순수 판정(엔티티 받아 boolean 반환, DB·throw 없음).
public final class UserPolicy {

    private UserPolicy() {
    }

    // "이 사용자를 쓸 수 있나" = 재직중(ACTIVE). 휴직·퇴사는 모두 비활성으로 본다.
    // (일정 도메인의 "휴직 허용, 퇴사만 거부"는 규칙이 달라 이 술어를 쓰지 않는다 — 필요 시 isEmployed() 별도 추가)
    public static boolean isActive(UserEntity user) {
        return user.getStatus() == StatusType.ACTIVE;
    }
}
