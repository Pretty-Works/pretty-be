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
public class MeetingController {
    private final MeetingService meetingService;

    // 회의록 작성
    @Operation(
            summary = "회의록 작성",
            description = """
                    특정 프로젝트를 선택하여 회의록을 작성합니다.
                    
                    - 문서번호는 서버에서 자동으로 지정 및 저장됩니다.
                    - authorId는 토큰(로그인 정보)에서 확인하여, meeting_attendees 테이블에 role=WRITER 로 저장됩니다.
                    - attendeeIds를 기반으로 참석자들을 meeting_attendees 테이블에 role=ATTENDEE 로 저장합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회의록 작성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "meetingId": 1 } }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "회의명은 필수입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트 또는 참석자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_003", "message": "존재하지 않는 프로젝트입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "중복된 회의록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEETING_004", "message": "동일한 문서번호의 회의록이 이미 존재합니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @PostMapping("/api/v1/projects/{projectId}/meetings")
    public ResponseEntity<MeetingCreateResponse> createMeeting(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long authorId,
            @Valid @RequestBody MeetingCreateRequest request) {
        MeetingCreateResponse response = meetingService.createMeeting(projectId, authorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 회의록 목록 조회
    @Operation(summary = "회의록 목록 조회", description = "프로젝트의 회의록 목록을 검색·페이징하여 조회합니다.")
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
                                            "title": "프로젝트 킥오프 회의",
                                            "authorName": "김피엠",
                                            "attendeeNames": ["이팀장", "박사원", "최선임"],
                                            "meetingDate": "2026-07-20"
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
            @ApiResponse(responseCode = "400", description = "잘못된 요청(page/size 형식 오류 등)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "RESPONSE_001", "message": "서버와의 연결에 실패했습니다.", "result": null }
                                    """)))
    })
    @GetMapping("/api/v1/projects/{projectId}/meetings")
    public ResponseEntity<PageResponse<MeetingListResponse>> getMeetingList(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
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
                    프로젝트에 속한 회의록 1건의 상세 정보를 조회합니다.
                    
                    - meetingId로 회의록을 찾고, 참석자를 작성자(author)와 참석자(attendees)로 나누어 반환합니다.
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
                                        "documentNumber": "MTG-2026-07-20-1",
                                        "title": "프로젝트 킥오프 회의",
                                        "meetingDate": "2026-07-20",
                                        "location": "회의실 A",
                                        "author": { "name": "김피엠", "department": "PM" },
                                        "attendees": [
                                          { "name": "박사원", "department": "BACKEND" },
                                          { "name": "최선임", "department": "FRONTEND" }
                                        ],
                                        "recording": "voice1.mp3",
                                        "purpose": "프로젝트 진행 방향 논의",
                                        "content": "일정 및 역할 분담 논의",
                                        "followUp": "API 명세 작성 후 공유"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청(경로 변수 형식 오류 등)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "회의록 또는 프로젝트를 찾을 수 없음",
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
    @GetMapping("/api/v1/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetail(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {
        MeetingDetailResponse response = meetingService.getMeetingDetail(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }

    // 회의록 수정
    @Operation(
            summary = "회의록 수정",
            description = """
                    회의록 1건을 수정합니다.
                    
                    - 회의 내용(제목·일자·장소·목적·내용·후속조치·녹취)을 수정합니다.
                    - 참석자는 기존 명단을 지우고 attendeeIds로 새로 저장합니다. (작성자는 유지)
                    - 문서번호·작성자·소속 프로젝트는 변경되지 않습니다.
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
                                        "documentNumber": "MTG-2026-07-20-1",
                                        "title": "프로젝트 킥오프 회의(수정)",
                                        "meetingDate": "2026-07-21",
                                        "location": "회의실 B",
                                        "author": { "name": "김피엠", "department": "PM" },
                                        "attendees": [
                                          { "name": "이팀장", "department": "BACKEND" },
                                          { "name": "정파트", "department": "PLANNING" }
                                        ],
                                        "recording": "voice2.mp3",
                                        "purpose": "일정 재논의",
                                        "content": "역할 재분담",
                                        "followUp": "명세 재작성"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "회의명(title)은 필수입니다.", "result": null }
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
    @PutMapping("/api/v1/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> updateMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MeetingUpdateRequest request) {
        MeetingDetailResponse response = meetingService.updateMeeting(projectId, meetingId, userId, request);
        return ResponseEntity.ok(response);
    }

    // 회의록 삭제
    @Operation(summary = "회의록 삭제", description = "회의록을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회의록 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": null, "message": "SUCCESS", "result": { "meetingId": 1 } }
                                    """))),
            @ApiResponse(responseCode = "404", description = "회의록 또는 프로젝트를 찾을 수 없음",
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
    @DeleteMapping("/api/v1/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<MeetingDeleteResponse> deleteMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {
        MeetingDeleteResponse response = meetingService.deleteMeeting(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }
}