package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.MilestoneStatusRequest;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneListResponse;
import HK.PrettyWorks_BE.project.project.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 MilestoneApi 인터페이스에 있습니다.
@RestController
@RequiredArgsConstructor
public class MilestoneController implements MilestoneApi {

    private final MilestoneService milestoneService;

    // 프로젝트 개요 화면의 '마일스톤 완료율' 카드. 목록·집계·목표 마일스톤을 한 번에 반환합니다.
    @Override
    @GetMapping("/api/v1/projects/{projectId}/milestones")
    public ResponseEntity<MilestoneListResponse> getMilestones(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId
    ) {
        MilestoneListResponse response = milestoneService.getMilestones(projectId, userId);

        return ResponseEntity.ok(response);
    }

    // 타임라인의 체크(✓/○). 완료 여부만 바꾸며 목표일·내용은 프로젝트 수정 API에서 다룹니다.
    @Override
    @PatchMapping("/api/v1/projects/{projectId}/milestones/{milestoneId}/status")
    public Void toggleStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneStatusRequest request
    ) {
        milestoneService.toggleStatus(projectId, milestoneId, userId, request.done());

        // 반환값 없음 → 인터셉터가 BaseResponse로 감싸 result: null 응답을 만든다.
        return null;
    }
}
