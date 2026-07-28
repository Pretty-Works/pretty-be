package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectStatusRequest;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectListResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectStatusResponse;
import HK.PrettyWorks_BE.project.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// @Validated: @RequestHeader 등 메서드 파라미터의 제약(@Size)을 검증하려면 필요합니다. (없으면 조용히 무시됨)
@RestController
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private final ProjectService projectService;

    // 호출자(로그인 사용자)가 오너가 되어 프로젝트를 생성합니다.
    @Operation(summary = "프로젝트 생성",
            description = "직급 팀장 이상 또는 부서 PM만 생성 가능. 오너·참여자·마일스톤을 함께 등록. "
                    + "Idempotency-Key 헤더로 중복 생성을 방지할 수 있음")
    @PostMapping("/api/v1/projects")
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal Long ownerId,
            @Parameter(description = "중복 생성 방지용 멱등 키(선택). 폼이 열릴 때 UUID v4를 발급해 두고, "
                    + "연타·재시도 시 같은 키를 재사용하면 첫 응답이 그대로 반환됩니다.",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @Size(max = 64, message = "Idempotency-Key는 64자 이하여야 합니다.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectResponse response = projectService.create(ownerId, idempotencyKey, request);

        return ResponseEntity.ok(response);
    }

    // 홈의 '진행 중 프로젝트' 패널과 프로젝트 선택 팝업이 함께 사용합니다.
    @Operation(summary = "프로젝트 목록 조회",
            description = "참여중인 프로젝트를 상태·이름으로 걸러 페이지 단위로 조회. 기본은 진행중만. "
                    + "정렬은 서버 고정(진행중→보류→종료, 진행형은 마감 임박순·종료형은 최근 종료순)")
    @GetMapping("/api/v1/projects")
    public ResponseEntity<PageResponse<ProjectListResponse>> getMyProjects(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 상태. ONGOING/HOLDING/COMPLETED/DROPPED 또는 전체를 뜻하는 ALL",
                    example = "ONGOING")
            @RequestParam(defaultValue = "ONGOING") String status,
            @Parameter(description = "프로젝트명 부분 일치 검색어", example = "그룹웨어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터)")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @Parameter(description = "한 페이지당 개수 (1~100)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        PageResponse<ProjectListResponse> response =
                projectService.getMyProjects(userId, status, keyword, PageRequest.of(page, size));

        return ResponseEntity.ok(response);
    }

    // 수정 화면 진입용 상세 조회. 낙관적 락에 필요한 version을 함께 내려줍니다.
    @Operation(summary = "프로젝트 상세 조회",
            description = "참여중인 멤버면 누구나 조회 가능. 수정 폼용 현재 값 + 낙관적 락 version + 진행률(파생) 반환")
    @GetMapping("/api/v1/projects/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId
    ) {
        ProjectDetailResponse response = projectService.getDetail(userId, projectId);

        return ResponseEntity.ok(response);
    }

    // 대상 프로젝트의 기본 정보·참여자·마일스톤을 수정합니다. 상세 조회에서 받은 version을 헤더로 되돌려받아 동시 수정을 막습니다.
    @Operation(summary = "프로젝트 수정",
            description = "대상 프로젝트의 오너 또는 프로젝트 내 역할이 PM인 사용자만 수정 가능. "
                    + "X-Resource-Version 헤더로 동시 수정(덮어쓰기)을 차단")
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
