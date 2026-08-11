package HK.PrettyWorks_BE.calendar.schedule.controller;

import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleUpdateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse.ScheduleItem;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleUpdateResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Swagger 문서는 ScheduleApi 인터페이스에 있다. 여기는 매핑·바인딩·로직만.
@RestController
@RequiredArgsConstructor
public class ScheduleController implements ScheduleApi {

    private final ScheduleService scheduleService;

    @Override
    @PostMapping("/api/v1/calendar/schedules")
    public ResponseEntity<ScheduleCreateResponse> create(
            @AuthenticationPrincipal Long writerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleCreateResponse response = scheduleService.create(writerId, idempotencyKey, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
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

    // 조회는 완전공개라 @AuthenticationPrincipal을 받지 않는다 — 쓰지도 않을 값을 시그니처에 두면
    // 권한 판정이 있는 것처럼 읽힌다.
    @Override
    @GetMapping("/api/v1/calendar/schedules/{scheduleId}")
    public ResponseEntity<ScheduleItem> get(
            @PathVariable Long scheduleId
    ) {
        ScheduleItem response = scheduleService.get(scheduleId);

        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/api/v1/calendar/schedules/{scheduleId}")
    public Void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long scheduleId
    ) {
        scheduleService.delete(userId, scheduleId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }

    @Override
    @DeleteMapping("/api/v1/calendar/schedules/{scheduleId}/me")
    public Void leave(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long scheduleId
    ) {
        scheduleService.leave(userId, scheduleId);

        return null;
    }

    @Override
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
