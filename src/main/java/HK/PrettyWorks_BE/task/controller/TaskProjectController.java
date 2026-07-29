package HK.PrettyWorks_BE.task.controller;

import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Swagger 문서는 TaskProjectApi 인터페이스에 있습니다.
@RestController
@RequiredArgsConstructor
public class TaskProjectController implements TaskProjectApi {

    private final TaskService taskService;

    // 프로젝트 인원 보드: 이번 주(+weekOffset) 할 일을 팀별로 그룹핑해 반환 (프로젝트 멤버만)
    @Override
    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public ResponseEntity<TaskProjectResponse> getTaskProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Parameter(description = "조회할 주. 0 이번 주, -1 지난 주, 1 다음 주", example = "0")
            @RequestParam(defaultValue = "0") int weekOffset
    ) {
        TaskProjectResponse response = taskService.getTaskProject(userId, projectId, weekOffset);

        return ResponseEntity.ok(response);
    }
}
