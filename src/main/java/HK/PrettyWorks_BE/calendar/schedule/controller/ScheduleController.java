package HK.PrettyWorks_BE.calendar.schedule.controller;

import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleCreateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.req.ScheduleUpdateRequest;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleCreateResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleListResponse;
import HK.PrettyWorks_BE.calendar.schedule.dto.res.ScheduleUpdateResponse;
import HK.PrettyWorks_BE.calendar.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "일정", description = "캘린더 일정(회의/외근/개인) 추가·조회·수정·삭제·나가기 API. 휴가는 별도 휴가 API로 신청/수정/취소한다.")
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 일정 추가
    @Operation(
            summary = "일정 추가",
            description = """
                    회의/외근/개인 일정을 생성합니다. 로그인 사용자가 작성자(WRITER)로 자동 등록됩니다.

                    [요청]
                    - title / startAt / endAt(yyyy-MM-dd'T'HH:mm:ss) 필수, type 필수(MEETING/FIELDWORK/PERSONAL)
                    - allDay: 종일이면 true(생략 시 false). true면 서버가 00:00:00~23:59:59로 정규화하므로 프론트는 날짜만 신경쓰면 됨
                    - participantUserIds: 함께 볼 참가자(작성자 제외, 선택). 본인·중복은 서버가 정리
                    - startAt ≤ endAt (위반 시 400 SCHEDULE_002)
                    [응답]
                    - result.scheduleId
                    [에러]
                    - 참가자 없음 404(USER_002), 퇴사자 포함 400(USER_003) — 휴직자는 허용
                    [중복 방어 — 프론트]
                    - 폼이 열릴 때 발급한 UUID를 Idempotency-Key 헤더로 보내면 중복 생성 방지. 같은 키+다른 내용만 409(REQUEST_028)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "일정 추가 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "scheduleId": 51 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 기간 오류(SCHEDULE_002) / 퇴사자 참가(USER_003)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_002", "message": "일정 종료일시는 시작일시 이후여야 합니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "401", description = "토큰의 사용자를 신뢰할 수 없음(작성자 조회 실패)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_005", "message": "자격 증명이 이루어지지 않았습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "지정한 참가자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "USER_002", "message": "존재하지 않는 사용자입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "같은 멱등 키로 다른 내용의 요청이 접수됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_028", "message": "동일한 요청 키로 다른 내용의 요청이 접수되었습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @PostMapping("/api/v1/calendar/schedules")
    public ResponseEntity<ScheduleCreateResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long writerId,
            @Parameter(description = "클라이언트 발급 UUID(선택). 버튼 연타·재시도 중복 방어용. 폼이 열릴 때 1회 발급하고 동일 폼의 재요청은 같은 키를 사용한다.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleCreateResponse response = scheduleService.create(writerId, idempotencyKey, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 일정 목록 조회
    @Operation(
            summary = "일정 목록 조회",
            description = """
                    캘린더에 표시할 일정을 기간으로 조회합니다.

                    [요청]
                    - from, to(yyyy-MM-dd) 필수: 이 기간과 겹치는 일정을 startAt 오름차순으로 반환
                    - userIds(선택): 함께 볼 사람들. 미전달 시 본인 일정만. 본인은 항상 포함, 존재하지 않는 userId는 무시
                    [응답] result.schedules[]
                    - 각 항목에 owner(작성자)와 participants(참가자, 이름 포함)
                    - isLeave=true면 휴가 일정이며 leaveId·leaveType·reason·days 동봉(일반 일정은 생략). leaveId는 휴가 수정/취소 API 호출 키, reason은 편집 모달 프리필용
                    - 이벤트 색은 유형(type)이 아니라 사람(owner)별로 칠하는 설계
                    - 접근 제한 없음: 누구나 임의 userId 조회 가능(팀 투명성)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일정 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "schedules": [
                                          {
                                            "id": 51,
                                            "title": "연차",
                                            "startAt": "2026-08-10T00:00:00",
                                            "endAt": "2026-08-12T23:59:59",
                                            "allDay": true,
                                            "type": "PERSONAL",
                                            "isLeave": true,
                                            "leaveId": 39,
                                            "leaveType": "ANNUAL",
                                            "reason": "개인 사정",
                                            "days": 3,
                                            "owner": { "userId": 3, "name": "김피엠" },
                                            "participants": [
                                              { "userId": 3, "name": "김피엠", "role": "WRITER" }
                                            ]
                                          }
                                        ]
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "조회 기간 오류(시작일 > 종료일)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_004", "message": "조회 종료일은 시작일 이후여야 합니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @GetMapping("/api/v1/calendar/schedules")
    public ResponseEntity<ScheduleListResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)", example = "2026-08-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)", example = "2026-08-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "함께 조회할 사용자 id 목록(선택). 본인은 항상 포함된다.")
            @RequestParam(required = false) List<Long> userIds
    ) {
        ScheduleListResponse response = scheduleService.list(userId, from, to, userIds);

        return ResponseEntity.ok(response);
    }

    // 일정 삭제
    @Operation(
            summary = "일정 삭제",
            description = """
                    본인이 작성한 일정을 삭제합니다(하드 삭제, 복구 불가).

                    - 작성자만 가능(SCHEDULE_003). 참가자로서 참여만 빼려면 '나가기'(DELETE .../{scheduleId}/me) 사용
                    - 없는 일정 404(SCHEDULE_001)
                    - 참가자·휴가 상세는 함께 정리됨. 성공 응답 result는 null
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일정 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인이 작성한 일정이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_003", "message": "본인이 작성한 일정만 수정·삭제할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_001", "message": "존재하지 않는 일정입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @DeleteMapping("/api/v1/calendar/schedules/{scheduleId}")
    public Void delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "삭제할 일정 id") @PathVariable Long scheduleId
    ) {
        scheduleService.delete(userId, scheduleId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }

    // 일정 나가기
    @Operation(
            summary = "일정 나가기",
            description = """
                    내가 참가자로 있는 일정에서 본인만 빠집니다(다른 사람에겐 영향 없음).

                    - 일반 참가자: 본인 참여만 제거(soft delete), 일정 자체는 유지
                    - 작성자(오너): 다른 참가자가 있으면 나갈 수 없음(400 SCHEDULE_005) → '삭제' 사용. 단 혼자면 나가기=일정 전체 삭제
                    - 참가자가 아니거나 이미 나갔으면 404(SCHEDULE_006)
                    - 성공 응답 result는 null
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "나가기 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": null }
                                    """))),
            @ApiResponse(responseCode = "400", description = "작성자는 나갈 수 없음(다른 참가자 존재)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_005", "message": "작성자는 나갈 수 없습니다. 전체 삭제를 이용해 주세요.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "일정이 없거나(SCHEDULE_001) 해당 일정의 참가자가 아님(SCHEDULE_006)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_006", "message": "해당 일정의 참가자가 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @DeleteMapping("/api/v1/calendar/schedules/{scheduleId}/me")
    public Void leave(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나갈 일정 id") @PathVariable Long scheduleId
    ) {
        scheduleService.leave(userId, scheduleId);

        return null;
    }

    // 일정 수정
    @Operation(
            summary = "일정 수정",
            description = """
                    본인이 작성한 일정을 수정합니다. 보낸 필드만 반영하고, 안 보낸 필드(null)는 기존값 유지.

                    [요청]
                    - title/startAt/endAt/allDay/type: 바꿀 값만 전달. allDay=true면 시간 정규화, startAt ≤ endAt(400 SCHEDULE_002)
                    - participantUserIds: null=참가자 유지 / [](빈 배열)=작성자 혼자로 축소 / 값 있음=그 목록으로 교체
                    [응답] result.scheduleId
                    [에러]
                    - 작성자 아님 403(SCHEDULE_003), 없는 일정 404(SCHEDULE_001)
                    - 휴가 일정은 이 API로 못 바꿈(400 SCHEDULE_007) → 휴가 수정 API 사용
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일정 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "scheduleId": 51 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "휴가 일정 수정 시도(SCHEDULE_007) / 기간 오류(SCHEDULE_002) / 입력값 검증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_007", "message": "휴가 일정은 휴가 수정 API로만 변경할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인이 작성한 일정이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_003", "message": "본인이 작성한 일정만 수정·삭제할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "SCHEDULE_001", "message": "존재하지 않는 일정입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @PatchMapping("/api/v1/calendar/schedules/{scheduleId}")
    public ResponseEntity<ScheduleUpdateResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "수정할 일정 id") @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        ScheduleUpdateResponse response = scheduleService.update(userId, scheduleId, request);

        return ResponseEntity.ok(response);
    }
}
