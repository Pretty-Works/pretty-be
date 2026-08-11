package HK.PrettyWorks_BE.project.meeting.controller;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingUpdateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDeleteResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.service.MeetingService;
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

    @Override
    @PostMapping
    public ResponseEntity<MeetingCreateResponse> createMeeting(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long authorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody MeetingCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(projectId, authorId, idempotencyKey, request));
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<MeetingListResponse>> getMeetingList(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String attendeeName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                meetingService.getMeetingList(
                        projectId,
                        userId,
                        title,
                        attendeeName,
                        pageable
                )
        );
    }

    @Override
    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> getMeetingDetail(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(
                meetingService.getMeetingDetail(projectId, meetingId, userId)
        );
    }

    @Override
    @PutMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> updateMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId,
            @RequestBody MeetingUpdateRequest request) {

        return ResponseEntity.ok(
                meetingService.updateMeeting(projectId, meetingId, userId, request)
        );
    }

    @Override
    @DeleteMapping("/{meetingId}")
    public ResponseEntity<MeetingDeleteResponse> deleteMeeting(
            @PathVariable Long projectId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(
                meetingService.deleteMeeting(projectId, meetingId, userId)
        );
    }
}