package HK.PrettyWorks_BE.calendar.leave.dto.res;

import HK.PrettyWorks_BE.calendar.leave.constant.LeaveType;
import HK.PrettyWorks_BE.calendar.leave.repository.LeaveListRow;
import lombok.Builder;

import java.time.LocalDate;

// 휴가 목록 한 건.
//
// 저장은 종일 일정(00:00:00~23:59:59)이지만 밖으로는 날짜만 내보낸다 — 휴가는 시각의 개념이 없고,
// 시분초를 그대로 넘기면 호출자마다 다르게 잘라 쓰게 된다.
//
// reason은 그대로 담는다. 누구에게 보여줄지는 호출자가 정한다 — 캘린더 화면은 전원 공개(팀 결정)이고,
// 에이전트는 답변에 옮겨 적을 수 있어 본인 것만 남긴다.
@Builder
public record LeaveListResponse(
        Long leaveId,
        Long scheduleId,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String reason,
        Long userId,
        String userName
) {
    public static LeaveListResponse from(LeaveListRow row) {
        return LeaveListResponse.builder()
                .leaveId(row.leaveId())
                .scheduleId(row.scheduleId())
                .leaveType(row.leaveType())
                .startDate(row.startAt().toLocalDate())
                .endDate(row.endAt().toLocalDate())
                .days(row.days() == null ? 0 : row.days())
                .reason(row.reason())
                .userId(row.userId())
                .userName(row.userName())
                .build();
    }
}
