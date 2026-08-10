package HK.PrettyWorks_BE.agent.tool.calendar.dto;

import HK.PrettyWorks_BE.calendar.schedule.constant.ScheduleType;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleUpdateRequest;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

// schedule.update 요청. 진짜 부분 수정이라 scheduleId를 뺀 모든 필드가 선택이고,
// 미전달(null)이면 기존 값이 유지된다.
//
// participantUserIds만 세 가지 뜻을 가진다 — null=유지 / []=작성자 혼자로 축소 / 값=전체 교체.
// "박지원님 추가"를 [박지원]으로 보내면 기존 참가자가 전부 빠진다.
public record AgentScheduleUpdateRequest(
        @NotNull Long scheduleId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        ScheduleType type,
        List<Long> participantUserIds
) {
    public ScheduleUpdateRequest toDomain() {
        return ScheduleUpdateRequest.builder()
                .title(title)
                .startAt(startAt)
                .endAt(endAt)
                .allDay(allDay)
                .type(type)
                .participantUserIds(participantUserIds)
                .build();
    }
}
