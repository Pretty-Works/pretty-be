package HK.PrettyWorks_BE.task.controller;

import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.req.TaskStatusRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // 로그인 사용자가 할 일을 생성합니다. (작성자 = 토큰 userId)
    @Operation(summary = "할 일 생성", description = "projectId 있으면 프로젝트 할 일(멤버만 가능), null이면 개인 할 일")
    @PostMapping("/api/v1/tasks")
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskRequest request
    ) {
        TaskResponse response = taskService.create(userId, request);

        return ResponseEntity.ok(response);
    }

    // 할 일의 내용·소속·마감을 수정합니다. (작성자 본인만)
    @Operation(summary = "할 일 수정", description = "작성자 본인만 가능. 전체 상태를 받아(PUT) 수정")
    @PutMapping("/api/v1/tasks/{taskId}")
    public ResponseEntity<TaskResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskRequest request
    ) {
        TaskResponse response = taskService.update(userId, taskId, request);

        return ResponseEntity.ok(response);
    }

    // 할 일을 삭제합니다. (작성자 본인만, hard delete)
    @Operation(summary = "할 일 삭제", description = "작성자 본인만 삭제 가능")
    @DeleteMapping("/api/v1/tasks/{taskId}")
    public ResponseEntity<TaskResponse> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId
    ) {
        TaskResponse response = taskService.delete(userId, taskId);

        return ResponseEntity.ok(response);
    }

    // 할 일 완료 상태를 토글합니다. (작성자 본인만) — 응답 result는 null
    @Operation(summary = "할 일 완료 토글", description = "작성자 본인만. done=true/false로 완료 상태 변경(멱등)")
    @PatchMapping("/api/v1/tasks/{taskId}/status")
    public ResponseEntity<Void> toggleStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskStatusRequest request
    ) {
        taskService.toggleStatus(userId, taskId, request);

        return ResponseEntity.ok().build();
    }

    // 로그인 사용자 본인의 할 일을 홈용으로 조회합니다. (프로젝트별 그룹, 미완료 + 완료 3일 이내, ARCHIVED 제외)
    @Operation(summary = "할 일 홈 조회", description = "본인 할 일을 프로젝트별로 그룹핑해 반환")
    @GetMapping("/api/v1/tasks")
    public ResponseEntity<TaskHomeResponse> getTaskHome(
            @AuthenticationPrincipal Long userId
    ) {
        TaskHomeResponse response = taskService.getTaskHome(userId);

        return ResponseEntity.ok(response);
    }
}
