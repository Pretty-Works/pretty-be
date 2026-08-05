package HK.PrettyWorks_BE.global.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 시작일·종료일을 모두 포함한 일수. 휴가 1일(8/10~8/10)이 0일로 계산되면 연차가 차감되지 않는다.
//
// 휴가 신청·수정·승인 카드 세 곳이 같은 숫자를 말해야 해서 규칙을 한 곳에 둔다.
public final class InclusiveDays {

    private InclusiveDays() {
    }

    public static int between(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
