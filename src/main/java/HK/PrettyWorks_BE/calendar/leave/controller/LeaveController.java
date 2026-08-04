package HK.PrettyWorks_BE.calendar.leave.controller;

import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveCreateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.req.LeaveUpdateRequest;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveBalanceResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveCreateResponse;
import HK.PrettyWorks_BE.calendar.leave.dto.res.LeaveUpdateResponse;
import HK.PrettyWorks_BE.calendar.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

// Swagger 문서는 LeaveApi 인터페이스에 있다. 여기는 매핑·바인딩·로직만.
@RestController
@RequiredArgsConstructor
public class LeaveController implements LeaveApi {

    private final LeaveService leaveService;

    @Override
    @PostMapping("/api/v1/calendar/leaves")
    public ResponseEntity<LeaveCreateResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody LeaveCreateRequest request
    ) {
        LeaveCreateResponse response = leaveService.create(userId, idempotencyKey, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/api/v1/calendar/leaves/balance")
    public ResponseEntity<LeaveBalanceResponse> getBalance(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer year
    ) {
        LeaveBalanceResponse response = leaveService.getBalance(userId, year);

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/api/v1/calendar/leaves/{leaveId}")
    public ResponseEntity<LeaveUpdateResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long leaveId,
            @Valid @RequestBody LeaveUpdateRequest request
    ) {
        LeaveUpdateResponse response = leaveService.update(userId, leaveId, request);

        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/api/v1/calendar/leaves/{leaveId}")
    public Void cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long leaveId
    ) {
        leaveService.cancel(userId, leaveId);

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }
}
