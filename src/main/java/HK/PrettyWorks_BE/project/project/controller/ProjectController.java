package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.ProjectCreateRequest;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectCreateResponse;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // 호출자(로그인 사용자)가 오너가 되어 프로젝트를 생성합니다.
    @Operation(summary = "프로젝트 생성", description = "직급 팀장 이상 또는 부서 PM만 생성 가능. 오너·참여자·마일스톤을 함께 등록")
    @PostMapping("/api/v1/projects")
    public ResponseEntity<ProjectCreateResponse> create(
            @AuthenticationPrincipal Long ownerId,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        ProjectCreateResponse response = projectService.create(ownerId, request);

        return ResponseEntity.ok(response);
    }
}
