package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectStatusRequest;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberResponse;
import HK.PrettyWorks_BE.project.member.service.ProjectMemberService;
import HK.PrettyWorks_BE.global.base.PageRequests;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectListResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectStatusResponse;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Swagger 문서는 ProjectApi 인터페이스에 있습니다.
// 파라미터 제약(@Min·@Size)을 붙이지 않습니다 — @Validated가 필요해지는데, 그러면 인터페이스에 없는 제약을
// 구현체가 재정의한 셈이라 HV000151로 이 컨트롤러의 모든 API가 500이 됩니다.
// 범위는 PageRequests, 멱등 키 길이는 IdempotencyService가 봅니다. (@Valid @RequestBody는 @Validated 없이 동작)
@RestController
@RequiredArgsConstructor
public class ProjectController implements ProjectApi {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    // 호출자(로그인 사용자)가 오너가 되어 프로젝트를 생성합니다.
    @Override
    @PostMapping("/api/v1/projects")
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal Long ownerId,
            @Parameter(description = "중복 생성 방지용 멱등 키(선택, 64자 이하). 폼이 열릴 때 UUID v4를 발급해 두고, "
                    + "연타·재시도 시 같은 키를 재사용하면 첫 응답이 그대로 반환됩니다.",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectResponse response = projectService.create(ownerId, idempotencyKey, request);

        return ResponseEntity.ok(response);
    }

    // 홈의 '진행 중 프로젝트' 패널과 프로젝트 선택 팝업이 함께 사용합니다.
    @Override
    @GetMapping("/api/v1/projects")
    public ResponseEntity<PageResponse<ProjectListResponse>> getMyProjects(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 상태. ONGOING/HOLDING/COMPLETED/DROPPED 또는 전체를 뜻하는 ALL",
                    example = "ONGOING")
            @RequestParam(defaultValue = "ONGOING") String status,
            @Parameter(description = "프로젝트명 부분 일치 검색어", example = "그룹웨어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지당 개수 (1~100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<ProjectListResponse> response =
                projectService.getMyProjects(userId, status, keyword, PageRequests.of(page, size));

        return ResponseEntity.ok(response);
    }

    // 수정 화면 진입용 상세 조회. 낙관적 락에 필요한 version을 함께 내려줍니다.
    @Override
    @GetMapping("/api/v1/projects/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId
    ) {
        ProjectDetailResponse response = projectService.getDetail(userId, projectId);

        return ResponseEntity.ok(response);
    }

    // 명단 표시와 참여자 추가 자동완성이 같은 엔드포인트를 쓴다. 자동완성은 keyword와
    // excludeSelf=true를 붙이고, 명단은 그대로 호출하면 된다.
    @Override
    @GetMapping("/api/v1/projects/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getMembers(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean excludeSelf,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<ProjectMemberResponse> response =
                projectMemberService.getMembers(projectId, requesterId, keyword, excludeSelf, size);

        return ResponseEntity.ok(response);
    }

    // 대상 프로젝트의 기본 정보·참여자·마일스톤을 수정합니다. 상세 조회에서 받은 version을 헤더로 되돌려받아 동시 수정을 막습니다.
    @Override
    @PutMapping("/api/v1/projects/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Parameter(description = "상세 조회(GET)에서 받은 version. 그 사이 다른 사용자가 먼저 수정했다면 409로 차단됩니다.",
                    example = "7", required = true)
            @RequestHeader("X-Resource-Version") Long version,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectResponse response = projectService.update(userId, projectId, version, request);

        return ResponseEntity.ok(response);
    }

    // 프로젝트의 진행 상태만 변경합니다.
    @Override
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
