package HK.PrettyWorks_BE.agent.interaction.api;

import HK.PrettyWorks_BE.agent.execution.api.AgentExecutionApi;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentApprovalRequest;
import HK.PrettyWorks_BE.agent.interaction.api.request.AgentQuestionAnswerRequest;
import HK.PrettyWorks_BE.agent.interaction.api.response.AgentPendingInteractionsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "에이전트 승인·질문", description = "에이전트 승인·질문 응답과 대기 목록 조회 API")
public interface AgentInteractionApi {

    @Operation(
            summary = "승인 요청 응답 및 실행 재개",
            description = """
                    승인 카드에 대한 결정을 보내고 멈춰 있던 실행을 이어갑니다. 응답은 새 SSE 스트림입니다.

                    * decision은 APPROVED · REJECTED · ALTERNATIVE 이며, ALTERNATIVE일 때만 alternativeId를 함께 보냅니다.
                    * alternativeId로 ALWAYS를 보내면 이 대화의 자동 승인이 켜집니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = AgentExecutionApi.SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "재개된 실행의 id",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = AgentExecutionApi.SSE_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "없는 approvalId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_012", "message": "요청을 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 응답한 카드(AGENT_006) / 시간이 지나 만료된 카드(AGENT_019)",
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
            @ApiResponse(responseCode = "200", description = AgentExecutionApi.SSE_DESCRIPTION,
                    headers = @Header(name = "X-Run-Id", description = "재개된 실행의 id",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "text/event-stream",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = AgentExecutionApi.SSE_EXAMPLE))),
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
                                    "label": "재계획 선택",
                                    "options": [
                                      { "id": "1", "label": "일정 연장", "description": "마일스톤 목표일을 2주 뒤로 미루고 작업 3건의 마감일도 함께 조정합니다. 리스크: 낮음" },
                                      { "id": "2", "label": "인력 재배치 (추천)", "description": "담당자를 재배치해 목표일을 유지합니다. 리스크: 중간" },
                                      { "id": "3", "label": "범위 축소", "description": null }
                                    ],
                                    "multiple": false,
                                    "conversationId": 15,
                                    "runId": "run_c02b58",
                                    "conversationTitle": "프로젝트 재계획",
                                    "previewText": null,
                                    "requestedAt": "2026-08-03 14:20:11",
                                    "expiresAt": "2026-08-03 14:50:11"
                                  },
                                  {
                                    "kind": "APPROVAL",
                                    "interactionId": 42,
                                    "label": "회의록 저장",
                                    "options": [
                                      { "id": "APPROVE", "label": "저장", "description": null },
                                      { "id": "FILL_FORM", "label": "직접 고칠래요", "description": null },
                                      { "id": "REJECT", "label": "취소", "description": null }
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
}
