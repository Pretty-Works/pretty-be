package HK.PrettyWorks_BE.global.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 주 경계가 어긋나면 user.me가 알려준 "이번 주"와 task.list가 조회한 "이번 주"가 달라진다.
// 에이전트는 이번 주 할 일을 물어보고 다른 주 목록을 받는데, 답변이 그럴듯해서 눈에 띄지 않는다.
class WeekRangeTest {

    // 일요일이 제일 위험하다. 주가 일요일에 시작한다고 보면 하루 통째로 밀린다.
    @Test
    void sundayBelongsToTheWeekThatAlreadyStarted() {
        WeekRange week = WeekRange.of(LocalDate.of(2026, 8, 2));

        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void mondayIsTheFirstDayOfItsOwnWeek() {
        WeekRange week = WeekRange.of(LocalDate.of(2026, 7, 27));

        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    // 상대 주차는 항상 그 주의 월요일에서 이동한다 — 오늘에서 7일씩 더하는 방식이면
    // 일요일 기준으로 계산할 때 주 경계를 넘지 못한다.
    @Test
    void offsetMovesWholeWeeksFromTheMonday() {
        LocalDate sunday = LocalDate.of(2026, 8, 2);

        assertThat(WeekRange.of(sunday, 1).start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(WeekRange.of(sunday, 1).end()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(WeekRange.of(sunday, -1).start()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(WeekRange.of(sunday, -1).end()).isEqualTo(LocalDate.of(2026, 7, 26));
    }
}
