package HK.PrettyWorks_BE.calendar.schedule.controller;

import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleUpdateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleUpdateResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 로그인 사용자가 작성자(WRITER)가 되어 일정을 생성합니다. participantUserIds는 PARTICIPANT로 등록됩니다.
    @Operation(summary = "일정 추가", description = "회의/외근/개인 일정 생성. 작성자 자동 참가, 참가자 지정 가능")
    @PostMapping("/api/v1/calendar/schedules")
    public ResponseEntity<ScheduleCreateResponse> create(
            @AuthenticationPrincipal Long writerId,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleCreateResponse response = scheduleService.create(writerId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 본인 + userIds가 참가자인 일정을 기간(겹침)으로 조회합니다. userIds 미전달 시 본인 일정만.
    @Operation(summary = "일정 목록 조회", description = "본인·userIds가 참가자인 일정을 from~to 기간 겹침으로 조회(startAt 오름차순)")
    @GetMapping("/api/v1/calendar/schedules")
    public ResponseEntity<ScheduleListResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> userIds
    ) {
        ScheduleListResponse response = scheduleService.list(userId, from, to, userIds);

        return ResponseEntity.ok(response);
    }

    // 본인이 작성한 일정만 하드 삭제합니다. 참가자·휴가 상세는 FK CASCADE로 함께 정리됩니다.
    @Operation(summary = "일정 삭제", description = "본인 작성 일정만 삭제. 참가자·휴가 상세는 CASCADE 정리")
    @DeleteMapping("/api/v1/calendar/schedules/{scheduleId}")
    public Void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long scheduleId
    ) {
        scheduleService.delete(userId, scheduleId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }

    // 본인이 작성한 일정만 부분 수정합니다. 전달된 필드만 반영하고, participantUserIds 전달 시 참가자를 교체합니다.
    @Operation(summary = "일정 수정", description = "본인 작성 일정만 부분 수정. 전달된 필드만 반영, 참가자 교체 가능")
    @PatchMapping("/api/v1/calendar/schedules/{scheduleId}")
    public ResponseEntity<ScheduleUpdateResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        ScheduleUpdateResponse response = scheduleService.update(userId, scheduleId, request);

        return ResponseEntity.ok(response);
    }
}
