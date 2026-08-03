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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/meetings")
public class MeetingController implements MeetingApi {
    private final MeetingService meetingService;

    // 회의록 작성
    @Override
    @PostMapping
    public ResponseEntity<MeetingCreateResponse> createMeeting(
            @PathVariable Long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long authorId,
            @Parameter(description = "중복 생성 방지용 멱등 키. 폼 열릴 때 UUID v4 발급해 두고 연타·재시도 시 같은 키 재사용",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @Size(max = 64, message = "Idempotency-Key는 64자 이하여야 합니다.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MeetingCreateRequest request) {
        MeetingCreateResponse response = meetingService.createMeeting(projectId, authorId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 회의록 목록 조회
    @Override
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
    @Override
    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetail(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        MeetingDetailResponse response = meetingService.getMeetingDetail(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }

    // 회의록 수정
    @Override
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
    @Override
    @DeleteMapping("/{meetingId}")
    public ResponseEntity<MeetingDeleteResponse> deleteMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        MeetingDeleteResponse response = meetingService.deleteMeeting(projectId, meetingId, userId);
        return ResponseEntity.ok(response);
    }
}