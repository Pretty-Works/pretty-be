package HK.PrettyWorks_BE.user.policy;

import HK.PrettyWorks_BE.user.constant.StatusType;
import HK.PrettyWorks_BE.user.domain.UserEntity;

// 사용자 관련 규칙. 순수 판정(엔티티 받아 boolean 반환, DB·throw 없음).
public final class UserPolicy {

    private UserPolicy() {
    }

    // "지금 업무에 참여할 수 있나" = 재직중(ACTIVE). 휴직·퇴사는 모두 거부.
    // 프로젝트 참여자·지출 등록 등 업무 행위의 기준. isEmployed의 부분집합이다.
    public static boolean isActive(UserEntity user) {
        return user.getStatus() == StatusType.ACTIVE;
    }

    // "아직 회사 소속인가" = 퇴사(RESIGNED)만 거부. 휴직(ON_LEAVE)은 포함.
    // 로그인 가능 여부와 일정 참가자 등록의 공통 기준.
    // TODO: 일정 도메인(ScheduleService)의 RESIGNED 직접 비교도 이 술어로 통합 예정 (담당자 작업)
    public static boolean isEmployed(UserEntity user) {
        return user.getStatus() != StatusType.RESIGNED;
    }
}
