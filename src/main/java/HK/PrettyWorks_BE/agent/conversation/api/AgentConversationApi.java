package HK.PrettyWorks_BE.agent.conversation.api;

import HK.PrettyWorks_BE.agent.conversation.api.request.AgentAutoApproveRequest;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentAutoApproveResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationDeleteResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationListResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentConversationReadResponse;
import HK.PrettyWorks_BE.agent.conversation.api.response.AgentMessagesResponse;
import HK.PrettyWorks_BE.global.base.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "에이전트 대화", description = "에이전트 대화 목록·메시지 조회와 읽음·자동 승인·삭제 API")
public interface AgentConversationApi {

    @Operation(
            summary = "에이전트 대화 목록 조회",
            description = """
                    최근 대화를 lastMessageAt 내림차순으로 돌려줍니다. 패널의 '최근 대화' 3건도 size만 달리 해서 같이 씁니다.

                    [스크롤 페이지네이션] page 번호가 아니라 cursor를 씁니다. 대화는 답변이 오갈 때마다 맨 위로
                    올라오므로 offset 방식이면 스크롤 도중 순서가 밀려 같은 대화가 두 번 오거나 빠집니다.
                    첫 페이지는 cursor 없이 호출하고, 다음 페이지는 응답의 nextCursor를 **그대로** 넘기세요
                    (값의 생김새는 서버 사정이므로 열어보거나 조립하지 마세요). hasNext가 false면 더 없습니다.
                    전체 개수는 내려주지 않습니다.

                    * status·runId는 가장 최근 실행 기준이며, WAITING_APPROVAL·WAITING_INPUT·RUNNING이면 재연결·취소할 수 있습니다.
                    * pendingApprovalId가 있으면 '확인 필요' 배지를 그리고, 그 id로 승인 응답 API를 호출합니다.
                    * unread는 읽음 처리 API로만 꺼집니다. 메시지 조회만으로는 꺼지지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화 목록 (대화가 없으면 items가 빈 배열, nextCursor는 null)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": {
                                        "items": [
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
                                        "nextCursor": "2026-08-02T14:25:30_12",
                                        "hasNext": true
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "size 범위를 벗어남 / 손상된 cursor",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """)))
    })
    ResponseEntity<CursorResponse<AgentConversationListResponse, String>> getConversations(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다. "
                    + "우리가 내려준 값이 아니면 400(REQUEST_001).", example = "2026-08-02T14:25:30_12")
            String cursor,
            @Parameter(description = "한 번에 가져올 개수 (1~100). 패널의 '최근 대화'는 3, 전체보기는 기본값을 씁니다. "
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
            summary = "에이전트 대화 삭제",
            description = """
                    대화 내역에서 고른 대화를 지웁니다. 요청 본문은 없습니다.

                    * soft delete입니다. 대화 목록·메시지 조회·대기 중 승인 카드에서 즉시 사라지지만
                      말풍선과 실행 이력은 DB에 남습니다(deleted_at 만 기록). 복구 API는 아직 없습니다.
                    * 진행 중인 실행(RUNNING·WAITING_APPROVAL·WAITING_INPUT)이 있으면 409로 거절합니다.
                      먼저 취소하거나 승인·질문에 응답한 뒤 다시 호출하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 결과",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AgentConversationDeleteResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "errorCode": null,
                                      "message": "SUCCESS",
                                      "result": { "conversationId": 12 }
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인의 대화가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_001", "message": "본인의 대화가 아닙니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 conversationId (이미 지운 대화 포함)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_002", "message": "대화를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "진행 중인 실행이 있음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_004", "message": "진행 중인 요청이 있습니다. 끝나기를 기다리거나 먼저 응답해 주세요.", "result": null }
                                    """)))
    })
    ResponseEntity<AgentConversationDeleteResponse> deleteConversation(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "지울 대화 id", example = "12") Long conversationId);

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
