package HK.PrettyWorks_BE.global.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 월요일 시작 ~ 일요일 끝 주 범위.
//
// "이번 주가 언제냐"는 정의를 한 곳에만 둔다. 주간 조회(task.list)와 에이전트가 날짜 계산의
// 기준으로 삼는 값(user.me의 thisWeekStart)이 서로 다른 월요일을 가리키면,
// 에이전트는 "이번 주 할 일"을 물어보고 다른 주의 목록을 받는다.
public record WeekRange(LocalDate start, LocalDate end) {

    // baseDate가 속한 주의 월요일에서 weekOffset주만큼 이동한 한 주. 0=이번 주, -1=지난 주, 1=다음 주.
    public static WeekRange of(LocalDate baseDate, int weekOffset) {
        LocalDate start = baseDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(weekOffset);
        return new WeekRange(start, start.plusDays(6));
    }

    public static WeekRange of(LocalDate baseDate) {
        return of(baseDate, 0);
    }

    // 그 날짜가 이 주에 속하는지. 양끝(월요일·일요일 당일) 포함.
    //
    // "지금 보고 있는 주가 이번 주인가"를 판단하는 데 쓴다 — 지연된 할 일을 함께 보여줄지가
    // 여기서 갈린다. 주의 경계 판정을 호출부가 직접 하면 월요일의 정의가 또 갈라진다.
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
