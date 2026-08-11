package HK.PrettyWorks_BE.agent.suggestion.api;

import HK.PrettyWorks_BE.agent.suggestion.dto.AgentSuggestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "에이전트 추천 문구", description = "에이전트 패널 추천 칩 API")
public interface AgentSuggestionApi {

    String SUGGESTIONS_EXAMPLE = """
            {
              "errorCode": null,
              "message": "SUCCESS",
              "result": {
                "suggestions": [
                  {
                    "text": "'환율 연동 검증' 마감이 지났습니다. 처리해드릴까요?",
                    "prompt": "'환율 연동 검증' 마감이 지난 할 일을 처리해줘",
                    "kind": "overdue_task"
                  },
                  {
                    "text": "'스프린트 리뷰 3차' 후속 액션을 정리해드릴까요?",
                    "prompt": "'스프린트 리뷰 3차' 회의의 후속 액션을 정리해줘",
                    "kind": "meeting_followup"
                  },
                  {
                    "text": "'그룹웨어 AI 고도화' 프로젝트 점검해드릴까요?",
                    "prompt": "'그룹웨어 AI 고도화' 프로젝트를 점검해줘",
                    "kind": "project_check"
                  }
                ]
              }
            }
            """;

    String EMPTY_EXAMPLE = """
            {
              "errorCode": null,
              "message": "SUCCESS",
              "result": { "suggestions": [] }
            }
            """;

    @Operation(
            summary = "에이전트 패널 추천 문구 조회",
            description = """
                    패널을 열 때 이 사용자의 실제 상황(밀린 할 일·정리 안 된 회의·임박 마감·다가오는 회의·남은 연차)에서
                    만든 추천 칩을 0~3개 돌려줍니다. 재료는 토큰의 사용자로 서버가 모으므로 프론트가 보낼 것은 없습니다.

                    * **칩을 누르면 `text`가 아니라 `prompt`를 메시지 전송 API의 `goal`로 보내세요.** `text`는 "~해드릴까요?" 물음형이라 그대로 보내면 에이전트가 되묻습니다.
                    * `kind`는 아이콘 매핑용 고정 목록입니다 — `overdue_task`·`meeting_followup`·`due_soon`·`upcoming_meeting`·`project_check`·`leave`. 모르는 값이 오면 기본 아이콘으로 처리하세요.
                    * 만들 때마다 LLM을 부르므로 수 초가 걸립니다. 패널은 먼저 띄우고 추천 영역만 스켈레톤으로 두세요.
                    * **이 API는 실패하지 않습니다.** 에이전트 서버가 죽어도 200에 빈 배열입니다. 빈 배열이면 추천 영역을 숨기면 됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 칩(없으면 빈 배열)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AgentSuggestionResponse.class),
                            examples = {
                                    @ExampleObject(name = "추천 있음", value = SUGGESTIONS_EXAMPLE),
                                    @ExampleObject(name = "추천할 것이 없거나 생성 실패", value = EMPTY_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AUTH_001", "message": "인증에 실패했습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<AgentSuggestionResponse> getSuggestions(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "패널을 연 화면. 생략하면 HOME.",
                    schema = @Schema(allowableValues = {"HOME", "PROJECT", "CALENDAR"}),
                    example = "HOME")
            String screen);
}
