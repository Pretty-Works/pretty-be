package HK.PrettyWorks_BE.calendar.schedule.controller;

import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
