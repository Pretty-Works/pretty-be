package HK.PrettyWorks_BE.agent.execution.api;

import HK.PrettyWorks_BE.agent.execution.api.request.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.execution.api.response.AgentCancelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "에이전트 실행", description = "에이전트 실행 시작·재연결·취소 API")
public interface AgentExecutionApi {

    // 실행 id를 싣는 응답 헤더. 승인·질문 응답도 같은 헤더로 나가므로
    // AgentInteractionController가 이 값을 가져다 쓴다.
    String RUN_ID_HEADER = "X-Run-Id";

    // SSE 응답 4곳이 똑같은 모양이라 예시를 한 벌만 둔다. 인터페이스 상수는 컴파일 타임 상수라
    // 애노테이션 안에서 그대로 쓸 수 있다. 각자 적어 두면 이벤트 규격이 바뀔 때 네 군데가 어긋난다.
    String SSE_EXAMPLE = """
            id: 1
            event: step
            data: {"text": "할 일을 만들고 있어요"}

            id: 2
            event: approval_request
            data: {"approvalId": 41, "toolCallId": "tc_1", "tool": "task.create", "access": "WRITE", "summary": "할 일 2건 추가", "previewText": "· 첫 번째 / · 두 번째", "params": {"tasks": [{"title": "첫 번째"}, {"title": "두 번째"}]}, "alternatives": [{"id": "FILL_FORM", "label": "직접 고칠래요"}, {"id": "ALWAYS", "label": "항상 허용"}], "autoApproved": false}

            id: 3
            event: done
            data: {"answer": "등록했습니다.", "action": {"type": "NAVIGATE", "label": "할 일 보기", "targetScreen": "TASK_LIST", "params": {"projectId": 3}}}
            """;

    String SSE_DESCRIPTION = """
            스트림이 열립니다(text/event-stream). 이벤트는 step · approval_request · question · done · error 다섯 가지이고,
            각 이벤트의 id는 재연결에 쓸 seq입니다.
            approval_request의 approvalId, question의 questionId가 이어지는 응답 API의 경로 변수입니다.
            Swagger UI에서는 본문이 보이지 않고 "Error: response status is 200"으로 끝납니다 — 정상입니다.
            """;

    @Operation(
            summary = "에이전트 실행 시작",
            description = """
                    메시지와 화면 문맥으로 Run을 만들고, 에이전트 이벤트를 SSE로 전달합니다.

                    * 요청은 multipart/form-data 입니다. request 파트에 본문을, files 파트에 첨부 파일을 담습니다.
                    * request 파트의 Content-Type은 application/json 이어야 합니다. (브라우저에서는 Blob으로 감싸 append)
                    * 첨부는 txt(UTF-8)만, 개당 1MB · 합계 2MB · 최대 3개까지 허용하며 하나라도 어기면 전부 거부됩니다.
                    * 파일을 첨부하면 goal을 생략할 수 있고, 둘 다 없는 요청은 거부됩니다.
                    * 첨부 파일은 저장하지 않으므로 대화 기록에는 파일명·크기만 남습니다.
                    * 진행 중인 실행이 있는 대화(409), 사용자당 실행 3건 초과(429)는 거부됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "이 실행의 id. 취소·재연결에 사용합니다.",
                            schema = @Schema(type = "string", example = "6f0f1f9c-6a1a-4c53-9d6e-2f0b0d9f1a77")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = SSE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류(REQUEST_001·REQUEST_026) / "
                    + "화면 정보 과대(AGENT_009) / 파일 형식·개수·인코딩 위반(AGENT_029·031·032)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "413", description = "파일 크기 초과(AGENT_030·REQUEST_027)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_030", "message": "파일이 너무 큽니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인의 대화가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_001", "message": "본인의 대화가 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "conversationId에 해당하는 대화 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_002", "message": "대화를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "그 대화에 진행 중인 실행이 있음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_004", "message": "진행 중인 요청이 있습니다. 끝나기를 기다리거나 먼저 응답해 주세요.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "429", description = "사용자당 진행 중 실행 3건 초과",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_018", "message": "진행 중인 작업이 너무 많습니다. 홈의 '확인이 필요한 요청'을 먼저 정리해 주세요.", "result": null }
                                    """)))
    })
    ResponseEntity<SseEmitter> send(
            @Parameter(hidden = true) Long userId,
            @Parameter(hidden = true) Authentication authentication,
            // schemaProperties가 있어야 Swagger UI가 파일 선택 버튼을 그리고,
            // encoding이 있어야 UI가 request를 JSON으로 보낸다(없으면 text/plain이라 415).
            // example을 통째로 주는 이유는 기존 JSON 바디 때와 같다 — 필드별로 조립하게 두면
            // conversationId가 채워져 그대로 실행하면 404가 난다.
            @RequestBody(required = true, content = @Content(
                    mediaType = "multipart/form-data",
                    schemaProperties = {
                            @SchemaProperty(name = "request", schema = @Schema(
                                    implementation = AgentMessageRequest.class,
                                    example = """
                                            {
                                              "conversationId": null,
                                              "goal": "이번 주 내 할 일 정리해 줘",
                                              "screenContext": { "screen": "TASK_LIST", "projectId": 3 }
                                            }
                                            """)),
                            @SchemaProperty(name = "files", array = @ArraySchema(
                                    schema = @Schema(type = "string", format = "binary"),
                                    arraySchema = @Schema(description = "첨부 파일(.txt). 없으면 생략한다.")))
                    },
                    encoding = @Encoding(name = "request", contentType = "application/json")))
            AgentMessageRequest request,

            @Parameter(hidden = true)
            java.util.List<org.springframework.web.multipart.MultipartFile> files);

    @Operation(
            summary = "에이전트 실행 스트림 재연결",
            description = """
                    끊긴 SSE를 다시 엽니다. 새 실행을 만들지 않으므로 409·429가 나지 않습니다.

                    * Last-Event-ID 헤더를 주면 그 다음 이벤트부터, 없으면 처음부터 재생합니다. EventSource는 자동으로 붙입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "재연결한 실행의 id",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = SSE_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "본인의 실행이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_011", "message": "본인의 실행이 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 runId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_010", "message": "실행을 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<SseEmitter> reconnect(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "실행 시작 응답의 X-Run-Id 헤더 값",
                    example = "6f0f1f9c-6a1a-4c53-9d6e-2f0b0d9f1a77")
            String runId,
            @Parameter(description = "마지막으로 받은 이벤트의 id(seq). 생략하면 처음부터 재생합니다.", example = "2")
            String lastEventId);

    @Operation(
            summary = "에이전트 실행 취소",
            description = """
                    진행 중인 실행을 중단합니다. 대기 중이던 승인·질문도 함께 닫히고 스트림에는 error 이벤트가 나갑니다.
                    이미 끝난 실행에 호출하면 canceled=false로 그때의 상태를 돌려줍니다(에러 아님).
                    """
    )
    @ApiResponses(@ApiResponse(responseCode = "200", description = "취소 처리 결과",
            content = @Content(mediaType = "application/json",
                    // schema를 명시하지 않으면 springdoc이 반환 타입에서 만든 스키마를 예시로 덮어써
                    // 프론트가 볼 필드 목록이 사라진다. 예시는 예시대로, 스키마는 스키마대로 남긴다.
                    schema = @Schema(implementation = AgentCancelResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "errorCode": null,
                              "message": "SUCCESS",
                              "result": {
                                "runId": "6f0f1f9c-6a1a-4c53-9d6e-2f0b0d9f1a77",
                                "status": "EXPIRED",
                                "canceled": true
                              }
                            }
                            """))))
    ResponseEntity<AgentCancelResponse> cancel(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "실행 시작 응답의 X-Run-Id 헤더 값",
                    example = "6f0f1f9c-6a1a-4c53-9d6e-2f0b0d9f1a77")
            String runId);
}
