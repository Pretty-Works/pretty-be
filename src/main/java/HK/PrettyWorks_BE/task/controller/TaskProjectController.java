package HK.PrettyWorks_BE.task.controller;

import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TaskProjectController {

    private final TaskService taskService;

    // 프로젝트 인원 보드: 이번 주(+weekOffset) 할 일을 팀별로 그룹핑해 반환 (프로젝트 멤버만)
    @Operation(summary = "프로젝트 인원 할 일 보드 조회",
            description = "프로젝트 멤버가 주 단위로 팀별 할 일·완료율을 조회. weekOffset=0 이번 주, -1 지난 주")
    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public ResponseEntity<TaskProjectResponse> getTaskProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int weekOffset
    ) {
        TaskProjectResponse response = taskService.getTaskProject(userId, projectId, weekOffset);

        return ResponseEntity.ok(response);
    }
}
