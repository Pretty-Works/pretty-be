package HK.PrettyWorks_BE.calendar.schedule.policy;

import HK.PrettyWorks_BE.calendar.schedule.domain.ScheduleEntity;

// 일정 관련 권한 규칙. 수정·삭제·전체삭제는 "작성자 본인(소유자)"만 가능하다.
// (project 도메인의 ExpensePolicy와 동일한 static 판정 패턴)
public final class SchedulePolicy {

    private SchedulePolicy() {
    }

    // 수정·삭제 공통: 작성자 본인만 가능(user_id == 호출자). 참가자·타인은 불가.
    public static boolean canModify(ScheduleEntity schedule, Long userId) {
        return schedule.getUserId().equals(userId);
    }
}
