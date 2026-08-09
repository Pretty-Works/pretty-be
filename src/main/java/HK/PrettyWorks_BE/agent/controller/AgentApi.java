package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.req.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentAutoApproveRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentMessageRequest;
import HK.PrettyWorks_BE.agent.dto.req.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.dto.res.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentCancelResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationListResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentConversationReadResponse;
import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentMessagesResponse;
import HK.PrettyWorks_BE.agent.dto.res.AgentPendingInteractionsResponse;
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

@Tag(name = "에이전트", description = "에이전트 대화·실행·승인·질문 응답 API")
public interface AgentApi {

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
            summary = "승인 요청 응답 및 실행 재개",
            description = """
                    승인 카드에 대한 결정을 보내고 멈춰 있던 실행을 이어갑니다. 응답은 새 SSE 스트림입니다.

                    * decision은 APPROVED · REJECTED · ALTERNATIVE 이며, ALTERNATIVE일 때만 alternativeId를 함께 보냅니다.
                    * alternativeId로 ALWAYS를 보내면 이 대화의 자동 승인이 켜집니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "재개된 실행의 id",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = SSE_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "없는 approvalId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_012", "message": "요청을 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 응답한 카드(AGENT_006) / 30분이 지나 만료된 카드(AGENT_019)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_006", "message": "이미 처리된 요청입니다.", "result": null }
                                    """)))
    })
    ResponseEntity<SseEmitter> resolveApproval(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "승인 카드 id. approval_request 이벤트의 approvalId, "
                    + "또는 대기 목록 조회의 interactionId.", example = "41")
            Long approvalId,
            @RequestBody(required = true, content = @Content(mediaType = "application/json",
                    examples = {
                            @ExampleObject(name = "승인", value = """
                                    { "decision": "APPROVED" }
                                    """),
                            @ExampleObject(name = "거절", value = """
                                    { "decision": "REJECTED", "reason": "금액이 잘못됐어요" }
                                    """),
                            @ExampleObject(name = "대안 선택", value = """
                                    { "decision": "ALTERNATIVE", "alternativeId": "FILL_FORM" }
                                    """)
                    }))
            AgentApprovalRequest request);

    @Operation(
            summary = "질문 응답 및 실행 재개",
            description = """
                    되묻기(question)에 답하고 멈춰 있던 실행을 이어갑니다. 응답은 새 SSE 스트림입니다.
                    선택지와 자유 입력은 함께 보낼 수 있고, 둘 다 비우면 "모르겠다"로 전달됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "재개된 실행의 id",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = SSE_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "없는 questionId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_012", "message": "요청을 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 답한 질문(AGENT_006) / 만료된 질문(AGENT_019)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_006", "message": "이미 처리된 요청입니다.", "result": null }
                                    """)))
    })
    ResponseEntity<SseEmitter> answerQuestion(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "질문 id. question 이벤트의 questionId, 또는 대기 목록 조회의 interactionId.",
                    example = "42")
            Long questionId,
            @RequestBody(required = true, content = @Content(mediaType = "application/json",
                    examples = {
                            @ExampleObject(name = "선택지 고르기", value = """
                                    { "selectedOptionIds": ["3"], "freeText": null }
                                    """),
                            @ExampleObject(name = "자유 입력", value = """
                                    { "selectedOptionIds": [], "freeText": "그룹웨어 프로젝트로 해 줘" }
                                    """)
                    }))
            AgentQuestionAnswerRequest request);

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

    @Operation(
            summary = "응답 대기 중인 승인·질문 조회",
            description = """
                    답을 기다리는 승인·질문 카드를 돌려줍니다. 홈의 '확인이 필요한 요청'이 이 응답을 씁니다.

                    * interactionId를 승인·질문 응답 API의 경로 변수로 그대로 씁니다.
                    * options[].id는 고른 뒤 되돌려 보내는 값입니다. QUESTION은 selectedOptionIds에 담고,
                      APPROVAL은 APPROVE→APPROVED, REJECT→REJECTED, 그 밖의 id는 ALTERNATIVE + alternativeId로 보냅니다.
                    """
    )
    @ApiResponses(@ApiResponse(responseCode = "200", description = "대기 중인 카드 목록(없으면 totalCount=0)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AgentPendingInteractionsResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "errorCode": null,
                              "message": "SUCCESS",
                              "result": {
                                "totalCount": 2,
                                "items": [
                                  {
                                    "kind": "QUESTION",
                                    "interactionId": 51,
                                    "label": "휴가 날짜 선택",
                                    "options": [
                                      { "id": "2026-03-02", "label": "3/2 (화)" },
                                      { "id": "2026-03-05", "label": "3/5 (금)" },
                                      { "id": "__FREE__", "label": "직접 입력" }
                                    ],
                                    "multiple": false,
                                    "conversationId": 15,
                                    "runId": "run_c02b58",
                                    "conversationTitle": "8월 휴가 일정 추천",
                                    "previewText": null,
                                    "requestedAt": "2026-08-03 14:20:11",
                                    "expiresAt": "2026-08-03 14:50:11"
                                  },
                                  {
                                    "kind": "APPROVAL",
                                    "interactionId": 42,
                                    "label": "회의록 저장",
                                    "options": [
                                      { "id": "APPROVE", "label": "저장" },
                                      { "id": "FILL_FORM", "label": "직접 고칠래요" },
                                      { "id": "REJECT", "label": "취소" }
                                    ],
                                    "multiple": false,
                                    "conversationId": 12,
                                    "runId": "run_7f3a91",
                                    "conversationTitle": "스프린트 리뷰 회의록 작성",
                                    "previewText": "· 회의명: 스프린트 리뷰 4차\\n· 일시: 2026-08-02\\n· 참석자: 김서준, 이하늘",
                                    "requestedAt": "2026-08-03 14:31:02",
                                    "expiresAt": "2026-08-03 15:01:02"
                                  }
                                ]
                              }
                            }
                            """))))
    ResponseEntity<AgentPendingInteractionsResponse> getPendingInteractions(
            @Parameter(hidden = true) Long userId);

    @Operation(
            summary = "에이전트 대화 목록 조회",
            description = """
                    최근 대화를 lastMessageAt 내림차순으로 돌려줍니다. 패널의 '최근 대화' 3건도 size만 달리 해서 같이 씁니다.

                    * status·runId는 가장 최근 실행 기준이며, WAITING_APPROVAL·WAITING_INPUT·RUNNING이면 재연결·취소할 수 있습니다.
                    * pendingApprovalId가 있으면 '확인 필요' 배지를 그리고, 그 id로 승인 응답 API를 호출합니다.
                    * unread는 읽음 처리 API로만 꺼집니다. 메시지 조회만으로는 꺼지지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "content": [
                                          {
                                            "conversationId": 15,
                                            "title": "8월 휴가 일정 추천",
                                            "status": "WAITING_APPROVAL",
                                            "runId": "run_c02b58",
                                            "pendingApprovalId": 44,
                                            "unread": true,
                                            "lastMessageAt": "2026-08-02 14:31:02",
                                            "createdAt": "2026-08-02 14:30:44"
                                          },
                                          {
                                            "conversationId": 12,
                                            "title": "스프린트 리뷰 회의록 작성",
                                            "status": "COMPLETED",
                                            "runId": "run_7f3a91",
                                            "pendingApprovalId": null,
                                            "unread": false,
                                            "lastMessageAt": "2026-08-02 14:25:30",
                                            "createdAt": "2026-08-02 14:19:02"
                                          }
                                        ],
                                        "page": 0,
                                        "size": 20,
                                        "totalElements": 2,
                                        "totalPages": 1,
                                        "last": true
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "page/size 범위를 벗어남",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """)))
    })
    ResponseEntity<PageResponse<AgentConversationListResponse>> getConversations(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            int page,
            @Parameter(description = "한 페이지당 개수 (1~100). 패널의 '최근 대화'는 3, 전체보기는 기본값을 씁니다. "
                    + "범위를 벗어나면 400(REQUEST_001).", example = "20")
            int size);

    @Operation(
            summary = "에이전트 대화 메시지 조회",
            description = """
                    대화 하나의 말풍선을 오래된 순으로 돌려줍니다.

                    * messages[]: role로 말풍선 방향을, success로 실패 안내 여부를 가릅니다.
                      steps는 '참고한 내용' 접이식, action은 NAVIGATE·FILL_FORM 버튼 동작에 씁니다.
                    * attachments: 그때 붙였던 파일(USER 행에만). 파일명·크기만 그리며 내려받기는 없습니다.
                    * approvals[]: 그 대화의 승인 카드를 결과까지 함께 돌려줍니다. status가 PENDING이면 아직 답을 기다리는 카드입니다.
                      messages[]와 별개 배열이라 섞어 그리려면 decidedAt·createdAt으로 정렬합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화 상세",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AgentMessagesResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "conversationId": 12,
                                        "title": "이번 주 할 일 정리",
                                        "autoApprove": false,
                                        "activeRunId": null,
                                        "activeRunStatus": null,
                                        "messages": [
                                          {
                                            "messageId": 101,
                                            "runId": 55,
                                            "role": "USER",
                                            "content": "이번 주 내 할 일 정리해 줘",
                                            "success": null,
                                            "steps": null,
                                            "actionType": null,
                                            "action": null,
                                            "attachments": [
                                              { "filename": "회의록.txt", "contentType": "text/plain", "sizeBytes": 20480 }
                                            ],
                                            "createdAt": "2026-08-06T14:05:00"
                                          },
                                          {
                                            "messageId": 102,
                                            "runId": 55,
                                            "role": "AGENT",
                                            "content": "등록했습니다.",
                                            "success": true,
                                            "steps": [{ "text": "할 일 3건을 찾았어요" }],
                                            "actionType": "NAVIGATE",
                                            "action": {
                                              "type": "NAVIGATE",
                                              "label": "할 일 보기",
                                              "targetScreen": "TASK_LIST",
                                              "params": { "projectId": 3 }
                                            },
                                            "attachments": [],
                                            "createdAt": "2026-08-06T14:05:09"
                                          }
                                        ],
                                        "approvals": [
                                          {
                                            "approvalId": 42,
                                            "seq": 5,
                                            "access": "WRITE",
                                            "summary": "회의록 저장 · 스프린트 리뷰 4차",
                                            "previewText": "· 회의명: 스프린트 리뷰 4차\\n· 일시: 2026-08-02",
                                            "alternatives": [
                                              { "id": "FILL_FORM", "label": "작성 화면에서 직접 고칠래요" }
                                            ],
                                            "status": "ALTERNATIVE",
                                            "chosenAlternativeId": "FILL_FORM",
                                            "decidedAt": "2026-08-02 14:25:12"
                                          }
                                        ]
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인의 대화가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_001", "message": "본인의 대화가 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 conversationId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_002", "message": "대화를 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<AgentMessagesResponse> getMessages(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "조회할 대화 id", example = "12") Long conversationId);

    @Operation(
            summary = "대화 읽음 처리",
            description = """
                    이 대화를 마지막 말풍선까지 읽음으로 표시해 목록의 unread를 끕니다. 메시지 조회로는 꺼지지 않습니다.

                    * 목록에서 대화를 열었을 때, 그리고 보고 있는 동안 스트림이 done으로 끝났을 때 호출합니다.
                    * 응답의 lastReadMessageId는 읽음으로 확정된 지점이며, 읽음 지점은 뒤로 가지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "읽음으로 확정된 지점",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AgentConversationReadResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": { "conversationId": 15, "lastReadMessageId": 102 }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인의 대화가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_001", "message": "본인의 대화가 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 conversationId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_002", "message": "대화를 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<AgentConversationReadResponse> markRead(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "읽음 처리할 대화 id", example = "15") Long conversationId);

    @Operation(
            summary = "대화 자동 승인 모드 전환",
            description = """
                    이 대화에서 승인 카드를 띄울지 여부를 바꿉니다. 켜면 이후 WRITE 도구도 카드 없이 바로 실행됩니다.
                    승인 카드에서 '항상 허용'(alternativeId=ALWAYS)을 고른 것과 같은 상태가 됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경된 상태",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AgentAutoApproveResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": { "conversationId": 12, "autoApprove": true }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 conversationId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_002", "message": "대화를 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<AgentAutoApproveResponse> changeAutoApprove(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "대상 대화 id", example = "12") Long conversationId,
            @RequestBody(required = true, content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            { "autoApprove": true }
                            """)))
            AgentAutoApproveRequest request);
}
