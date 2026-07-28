package HK.PrettyWorks_BE.calendar.leave.controller;

import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveCreateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveUpdateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveCreateResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 휴가 API의 Swagger 문서 전용 인터페이스. 컨트롤러(LeaveController)가 implements 하며,
// springdoc이 이 인터페이스의 애노테이션을 구현 메서드에 병합한다. → 컨트롤러 본문은 매핑·로직만 남긴다.
@Tag(name = "휴가", description = "연차·병가 신청/수정/취소 API. 휴가는 종일 일정(schedules) + 휴가 상세(schedule_leaves)로 저장된다.")
public interface LeaveApi {

    @Operation(
            summary = "휴가 신청",
            description = """
                    연차/병가를 신청합니다. 승인 없이 즉시 캘린더에 반영됩니다.

                    [요청]
                    - leaveType: ANNUAL(연차) / SICK(병가)
                    - startDate~endDate: 휴가 기간(yyyy-MM-dd). 하루면 두 값을 같게. startDate ≤ endDate (위반 시 400)
                    - reason: 사유(선택, 최대 255자). 목록조회에서 전원에게 노출될 수 있음
                    [응답]
                    - result.leaveId 반환. 이후 수정/취소는 이 leaveId로 호출
                    [중복 방어 — 프론트]
                    - 신청 폼이 열릴 때 UUID를 1회 발급해 Idempotency-Key 헤더로 전송하면 버튼 연타·재시도로 중복 신청되지 않습니다.
                    - 같은 키+같은 내용 재요청은 첫 응답을 그대로 반환(409 아님), 같은 키+다른 내용만 409(REQUEST_028). 409는 사용자에게 노출하지 말고 조용히 무시하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "휴가 신청 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "leaveId": 39 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 기간 오류(시작일 > 종료일)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
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
    ResponseEntity<LeaveCreateResponse> create(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "클라이언트 발급 UUID(선택). 버튼 연타·재시도 중복 방어용. 폼이 열릴 때 1회 발급하고 동일 폼의 재요청은 같은 키를 사용한다.")
            String idempotencyKey,
            LeaveCreateRequest request
    );

    @Operation(
            summary = "휴가 수정",
            description = """
                    본인이 신청한 휴가를 수정합니다. 휴가 편집 모달에서 사용합니다.

                    [요청] 모든 필드 선택 — 보낸 필드만 바뀌고, 안 보낸 필드(null)는 기존값 유지
                    - leaveType / startDate / endDate: 바꿀 값만 전달
                    - reason: null=기존 유지, ""(빈 문자열)=사유 지우기
                    [응답]
                    - 수정 후 최종값(leaveType/startDate/endDate/days/reason)을 반환하므로 모달을 이 값으로 갱신하면 됨. days는 서버가 재계산
                    [에러 처리]
                    - 남의 휴가 403(LEAVE_002), 없는 휴가 404(LEAVE_001), 기간 역전 400
                    - 범용 일정 수정 API(PATCH /calendar/schedules)로는 휴가를 못 바꿈(SCHEDULE_007) → 반드시 이 API 사용
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "휴가 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "leaveId": 39,
                                        "scheduleId": 51,
                                        "leaveType": "SICK",
                                        "startDate": "2026-08-10",
                                        "endDate": "2026-08-12",
                                        "days": 3,
                                        "reason": "개인 사정"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 기간 오류(시작일 > 종료일)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인이 신청한 휴가가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "LEAVE_002", "message": "본인이 신청한 휴가만 수정·취소할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 휴가",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "LEAVE_001", "message": "존재하지 않는 휴가입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<LeaveUpdateResponse> update(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "수정할 휴가 id") Long leaveId,
            LeaveUpdateRequest request
    );

    @Operation(
            summary = "휴가 취소",
            description = """
                    본인이 신청한 휴가를 취소합니다. 취소하면 캘린더에서 사라집니다(하드 삭제, 복구 불가).

                    - 성공 응답 result는 null
                    - 남의 휴가 403(LEAVE_002), 이미 없는 휴가 404(LEAVE_001)
                    - 되돌릴 수 없으므로 UI에서 확인 다이얼로그 권장
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "휴가 취소 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인이 신청한 휴가가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "LEAVE_002", "message": "본인이 신청한 휴가만 수정·취소할 수 있습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 휴가",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "LEAVE_001", "message": "존재하지 않는 휴가입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    Void cancel(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "취소할 휴가 id") Long leaveId
    );
}
