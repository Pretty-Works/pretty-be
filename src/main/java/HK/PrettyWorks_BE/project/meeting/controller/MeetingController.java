package HK.PrettyWorks_BE.project.meeting.controller;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingUpdateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDeleteResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회의록", description = "회의록 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/meetings")
public class MeetingController {
    private final MeetingService meetingService;

    // 회의록 작성
    @Operation(
            summary = "회의록 작성",
            description = """
                    특정 프로젝트에 회의록을 작성합니다.

                    - 문서번호는 서버에서 자동 생성됩니다. (형식: MTG-{회의일자}-{회의록 id})
                    - 작성자(로그인 사용자)는 role=WRITER, attendeeIds는 role=ATTENDEE 로 저장됩니다.
                    - 작성자는 해당 프로젝트의 참여중(ACTIVE) 멤버여야 하며, 참석자도 모두 해당 프로젝트의 재직·참여중 멤버여야 합니다.
                    - 완료/보관된 프로젝트, 프로젝트 기간을 벗어난 회의 일자, 작성자를 참석자에 포함하는 요청은 거부됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회의록 작성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "meetingId": 6 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 업무 규칙 위반",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_008", "message": "회의 일자가 프로젝트 기간을 벗어났습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트 또는 참석자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_002", "message": "참석자를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<MeetingCreateResponse> createMeeting(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long authorId,
            @Valid @RequestBody MeetingCreateRequest request) {
        MeetingCreateResponse response = meetingService.createMeeting(projectId, authorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 회의록 목록 조회
    @Operation(summary = "회의록 목록 조회", description = "프로젝트의 회의록 목록을 검색·페이징하여 조회합니다. (조회는 해당 프로젝트의 참여중 멤버만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회의록 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "content": [
                                          {
                                            "meetingId": 1,
                                            "title": "검색 고도화 킥오프",
                                            "authorName": "김피엠",
                                            "attendeeNames": ["이하늘", "최서아", "강지우"],
                                            "meetingDate": "2026-06-05"
                                          }
                                        ],
                                        "page": 0,
                                        "size": 10,
                                        "totalElements": 1,
                                        "totalPages": 1,
                                        "last": true
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<PageResponse<MeetingListResponse>> getMeetingList(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "회의명 검색어 (부분 일치)")
            @RequestParam(required = false) String title,
            @Parameter(description = "참석자 이름 검색어 (정확히 일치)")
            @RequestParam(required = false) String attendeeName,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지당 개수")
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<MeetingListResponse> response =
                meetingService.getMeetingList(projectId, userId, title, attendeeName, pageable);
        return ResponseEntity.ok(response);
    }

    // 회의록 상세 조회
    @Operation(
            summary = "회의록 상세 조회",
            description = """
                    프로젝트에 속한 회의록 1건의 상세 정보를 조회합니다. (해당 프로젝트의 참여중 멤버만 가능)

                    - 참석자를 작성자(author)와 참석자(attendees)로 나누어 반환하며, 각 인원의 userId를 함께 내려줍니다. (수정 화면 프리필용)
                    - URL의 projectId와 회의록의 소속 프로젝트가 일치하는지 검증합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회의록 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "meetingId": 1,
                                        "documentNumber": "MTG-2026-06-05-1",
                                        "title": "검색 고도화 킥오프",
                                        "meetingDate": "2026-06-05",
                                        "location": "본사 3층 회의실 A",
                                        "author": { "userId": 1, "name": "김피엠", "department": "PM" },
                                        "attendees": [
                                          { "userId": 2, "name": "이하늘", "department": "BACKEND" },
                                          { "userId": 4, "name": "최서아", "department": "FRONTEND" },
                                          { "userId": 6, "name": "강지우", "department": "DATA" }
                                        ],
                                        "recording": "recordings/mtg-2026-001.mp4",
                                        "purpose": "프로젝트 범위·일정 합의",
                                        "content": "범위, 역할, 마일스톤 확정",
                                        "followUp": "주간 스프린트 리뷰 운영"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "회의록을 찾을 수 없거나 해당 프로젝트 소속이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_005", "message": "존재하지 않는 회의록입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetail(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        MeetingDetailResponse response = meetingService.getMeetingDetail(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }

    // 회의록 수정
    @Operation(
            summary = "회의록 수정",
            description = """
                    회의록 1건을 수정합니다. (작성자 또는 참석자만 가능, 완료/보관 프로젝트는 불가)

                    - 회의 내용(제목·일자·장소·목적·내용·후속조치·녹취)을 수정합니다.
                    - 참석자는 기존 명단을 지우고 attendeeIds로 새로 저장합니다. (작성자는 WRITER로 유지)
                    - 문서번호·작성자·소속 프로젝트는 변경되지 않습니다.
                    - 참석자 본인이 수정할 때 자기 자신을 참석자 명단에서 제외할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회의록 수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "meetingId": 1,
                                        "documentNumber": "MTG-2026-06-05-1",
                                        "title": "검색 고도화 킥오프(수정)",
                                        "meetingDate": "2026-06-08",
                                        "location": "본사 3층 회의실 B",
                                        "author": { "userId": 1, "name": "김피엠", "department": "PM" },
                                        "attendees": [
                                          { "userId": 2, "name": "이하늘", "department": "BACKEND" },
                                          { "userId": 4, "name": "최서아", "department": "FRONTEND" }
                                        ],
                                        "recording": "recordings/mtg-2026-001-v2.mp4",
                                        "purpose": "범위 재조정",
                                        "content": "역할 재분담",
                                        "followUp": "명세 재작성"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 업무 규칙 위반",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_012", "message": "본인을 참석자 명단에서 제외할 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음(작성자·참석자 아님 / 프로젝트 미참여)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_010", "message": "회의록에 대한 권한이 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "회의록 또는 참석자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_005", "message": "존재하지 않는 회의록입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @PutMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> updateMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MeetingUpdateRequest request) {
        MeetingDetailResponse response = meetingService.updateMeeting(projectId, meetingId, userId, request);
        return ResponseEntity.ok(response);
    }

    // 회의록 삭제
    @Operation(summary = "회의록 삭제", description = "회의록을 소프트 삭제합니다. (작성자만 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회의록 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "meetingId": 1 } }
                                    """))),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음(작성자 아님 / 프로젝트 미참여)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_010", "message": "회의록에 대한 권한이 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "회의록을 찾을 수 없거나 해당 프로젝트 소속이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_011", "message": "해당 프로젝트의 회의록이 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @DeleteMapping("/{meetingId}")
    public ResponseEntity<MeetingDeleteResponse> deleteMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        MeetingDeleteResponse response = meetingService.deleteMeeting(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }
}