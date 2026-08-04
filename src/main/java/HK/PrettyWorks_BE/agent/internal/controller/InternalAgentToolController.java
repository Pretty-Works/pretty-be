package HK.PrettyWorks_BE.agent.internal.controller;

import HK.PrettyWorks_BE.agent.annotation.AgentUser;
import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.agent.internal.service.AgentProjectToolService;
import HK.PrettyWorks_BE.agent.service.AgentWriteExecutor;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import HK.PrettyWorks_BE.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InternalAgentToolController implements InternalAgentToolApi {

    private static final String TASK_CREATE = "task.create";
    private static final TypeReference<List<TaskRequest>> TASK_REQUESTS = new TypeReference<>() {};
    private static final TypeReference<List<TaskResponse>> TASK_RESPONSES = new TypeReference<>() {};

    private final AgentProjectToolService projectToolService;
    private final AgentWriteExecutor writeExecutor;
    private final TaskService taskService;

    @Override
    @GetMapping("/api/internal/agent/projects")
    public ResponseEntity<AgentPage<ProjectSearchResult>> searchProjects(
            @AgentUser Long userId,
            @RequestParam(defaultValue = "ONGOING") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectToolService.search(userId, status, keyword, size));
    }

    @Override
    @PostMapping(value = "/api/internal/agent/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TaskResponse>> createTasks(@AgentUser Long userId) {
        // Do not bind a business DTO in the controller. The executor first compares the exact raw
        // body with the approved canonical params, then converts and validates the batch.
        List<TaskResponse> response = writeExecutor.executeValidated(
                TASK_CREATE,
                Map.of(),
                TASK_REQUESTS,
                TASK_RESPONSES,
                (requests, ignoredLegacyIdempotencyKey) -> taskService.createBatch(userId, requests));
        return ResponseEntity.ok(response);
    }
}
