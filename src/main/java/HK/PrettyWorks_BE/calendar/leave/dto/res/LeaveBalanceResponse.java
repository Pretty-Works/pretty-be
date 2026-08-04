package HK.PrettyWorks_BE.calendar.leave.dto.res;

import lombok.Builder;

// 연차 현황 카드 응답. 캘린더 상단 카드와 휴가 페이지 카드가 공유한다.
@Builder
public record LeaveBalanceResponse(
        int year,
        int grantedDays,    // 그 해 부여일수(아직 부여 전이면 0)
        int usedDays,       // 그 해 사용한 연차(ANNUAL) 일수 합계. 공가(EXCUSED)은 제외
        int remainingDays,  // grantedDays - usedDays
        String tenureYears  // 근속연수 "N년 M개월"
) {
}
