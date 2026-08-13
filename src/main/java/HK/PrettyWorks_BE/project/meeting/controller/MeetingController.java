package HK.PrettyWorks_BE.project.meeting.controller;

import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingCreateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.req.MeetingUpdateRequest;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingCreateResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDeleteResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingDetailResponse;
import HK.PrettyWorks_BE.project.meeting.dto.res.MeetingListResponse;
import HK.PrettyWorks_BE.project.meeting.service.MeetingService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
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

        // 잘못된 page/size(page<0, size<1, size>100)는 PageRequests가 400으로 거부한다
        Pageable pageable = PageRequests.of(page, size);

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
            @Parameter(description = "상세 조회(GET)에서 받은 version. 그 사이 다른 사용자가 먼저 수정했다면 409로 차단됩니다.",
                    example = "3", required = true)
            @RequestHeader("X-Resource-Version") Long version,
            @RequestBody MeetingUpdateRequest request) {

        // 수정 트랜잭션이 커밋된 뒤 다시 조회해야 OPTIMISTIC_FORCE_INCREMENT로 올라간
        // 최종 version을 응답에 정확히 담을 수 있다.
        meetingService.updateMeeting(projectId, meetingId, userId, version, request);
        return ResponseEntity.ok(meetingService.getMeetingDetail(projectId, meetingId, userId));
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
