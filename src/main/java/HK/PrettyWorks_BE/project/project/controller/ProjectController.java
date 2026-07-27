package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectStatusRequest;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectStatusResponse;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // 호출자(로그인 사용자)가 오너가 되어 프로젝트를 생성합니다.
    @Operation(summary = "프로젝트 생성", description = "직급 팀장 이상 또는 부서 PM만 생성 가능. 오너·참여자·마일스톤을 함께 등록")
    @PostMapping("/api/v1/projects")
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal Long ownerId,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectResponse response = projectService.create(ownerId, request);

        return ResponseEntity.ok(response);
    }

    // 대상 프로젝트의 기본 정보·참여자·마일스톤을 수정합니다.
    @Operation(summary = "프로젝트 수정", description = "대상 프로젝트의 오너 또는 프로젝트 내 역할이 PM인 사용자만 수정 가능")
    @PutMapping("/api/v1/projects/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectResponse response = projectService.update(userId, projectId, request);

        return ResponseEntity.ok(response);
    }

    // 프로젝트의 진행 상태만 변경합니다. (오너 전용)
    @Operation(summary = "프로젝트 상태 변경", description = "대상 프로젝트의 오너만 상태 변경 가능")
    @PatchMapping("/api/v1/projects/{projectId}/status")
    public ResponseEntity<ProjectStatusResponse> changeStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectStatusRequest request
    ) {
        ProjectStatusResponse response = projectService.changeStatus(userId, projectId, request.status());

        return ResponseEntity.ok(response);
    }
}
