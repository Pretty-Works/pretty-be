package HK.PrettyWorks_BE.agent.internal.controller;

import HK.PrettyWorks_BE.agent.dto.res.AgentPage;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectSearchResult;
import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "에이전트 내부 도구", description = "FastAPI 전용 내부 도구 API")
public interface InternalAgentToolApi {

    @Operation(summary = "project.search", description = "승인 없이 요청자의 프로젝트를 검색합니다.")
    ResponseEntity<AgentPage<ProjectSearchResult>> searchProjects(
            Long userId, String status, String keyword, int size);

    @Operation(
            summary = "task.create",
            description = "승인된 배열 전체를 한 트랜잭션으로 생성합니다. 한 항목이라도 실패하면 전부 롤백합니다.",
            requestBody = @RequestBody(required = true, content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = TaskRequest.class))))
    )
    ResponseEntity<List<TaskResponse>> createTasks(Long userId);
}
