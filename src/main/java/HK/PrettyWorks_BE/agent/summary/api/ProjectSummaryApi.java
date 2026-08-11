package HK.PrettyWorks_BE.agent.summary.api;

import HK.PrettyWorks_BE.agent.summary.dto.ProjectSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "프로젝트 AI 요약", description = "프로젝트 탭 상단 AI 요약 배너 API")
public interface ProjectSummaryApi {

    String SUMMARY_EXAMPLE = """
            {
              "errorCode": null,
              "message": "SUCCESS",
              "result": {
                "projectId": 3,
                "generatedAt": "2026-08-07T04:00:12",
                "summaries": [
                  {
                    "section": "overview",
                    "headline": "프로젝트 50% 완료, 베타 오픈까지 14일 남았어요",
                    "detail": [
                      "지난주 리뷰 반영이 5일 지연됐어요.",
                      "API 명세 정리가 내일까지 마감이에요."
                    ],
                    "stats": [
                      { "label": "진행률", "value": "50%" },
                      { "label": "임박 마감", "value": "1건" },
                      { "label": "지연", "value": "1건" }
                    ]
                  },
                  {
                    "section": "board",
                    "headline": "HIGH 중요도 2건, 부하 테스트 지연 문제 확인해요",
                    "detail": ["HIGH 글은 부하 테스트 지연과 리스크 점검이에요."],
                    "stats": [{ "label": "전체", "value": "4건" }, { "label": "HIGH", "value": "2건" }]
                  },
                  {
                    "section": "budget",
                    "headline": "집행률 62%, 외주비 비중이 65%로 가장 커요",
                    "detail": ["집행률이 기간 경과율보다 8%p 빠르네요.", "잔여 예산은 ₩11,400,000 남았어요."],
                    "stats": [{ "label": "집행률", "value": "62%" }, { "label": "잔여", "value": "₩11.4M" }]
                  },
                  {
                    "section": "meeting",
                    "headline": "회의 2건 기록, 후속 액션 미정리 1건 있어요",
                    "detail": ["요구 재정의 킥오프 후속 액션이 미정리예요.", "다음 회의는 8월 6일 10시에 예정돼 있어요."],
                    "stats": [{ "label": "회의", "value": "2건" }, { "label": "후속 액션", "value": "미정리 1건" }]
                  }
                ]
              }
            }
            """;

    String EMPTY_EXAMPLE = """
            {
              "errorCode": null,
              "message": "SUCCESS",
              "result": { "projectId": 3, "generatedAt": null, "summaries": [] }
            }
            """;

    @Operation(
            summary = "프로젝트 AI 요약 조회",
            description = """
                    프로젝트 탭 상단의 AI 요약 배너를 돌려줍니다. 화면 진입은 이 API 하나로 끝납니다.

                    * section을 생략하면 overview → board → budget → meeting 4장을 한 번에 돌려줍니다. 탭마다 부를 필요는 없습니다.
                    * 보통 즉시 응답하지만, 요약이 없거나 프로젝트 데이터가 바뀌었으면 그 자리에서 만드느라 수 초가 걸립니다. 스켈레톤을 띄워 두세요.
                    * 데이터를 바꾼 뒤 다시 부르면 배너도 따라 바뀝니다. 단 마지막 생성으로부터 10분 안이면 이전 배너가 그대로 나옵니다.
                    * 만들지 못해도 200이며 summaries는 빈 배열, generatedAt은 null입니다. 배너만 그리지 않고 넘어가면 됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장된 배너(없으면 빈 배열)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProjectSummaryResponse.class),
                            examples = {
                                    @ExampleObject(name = "요약 있음", value = SUMMARY_EXAMPLE),
                                    @ExampleObject(name = "아직 생성 전", value = EMPTY_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "400", description = "모르는 section 값",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_001", "message": "잘못된 요청입니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "이 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 projectId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<ProjectSummaryResponse> getSummary(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "대상 프로젝트 id", example = "3") Long projectId,
            @Parameter(description = "한 섹션만 받을 때 지정합니다. 생략하면 4장 전부.",
                    schema = @Schema(allowableValues = {"overview", "board", "budget", "meeting"}),
                    example = "budget")
            String section);

    @Operation(
            summary = "프로젝트 AI 요약 갱신 (선택)",
            description = """
                    배너 4장을 다시 만들어 저장하고 돌려줍니다. 조회가 필요할 때 알아서 만들므로 필수는 아닙니다.

                    * 배너 숫자를 바꾸는 작업 직후나 사용자가 새로고침을 눌렀을 때 씁니다. 수 초가 걸리니 기다리게 하지 말고 도착하면 갈아 끼우세요.
                    * 연타해도 됩니다. 마지막 생성으로부터 10분 안이면 LLM을 부르지 않고 저장된 배너를 즉시 돌려줍니다.
                    * 조회와 달리 실패하면 502·504가 나갑니다. 이전 배너는 남아 있으니 화면은 두고 갱신 실패만 알리면 됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새로 만든 배너(최소 간격 이내면 기존 배너)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProjectSummaryResponse.class),
                            examples = @ExampleObject(value = SUMMARY_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "이 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 projectId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "429", description = "첫 생성이 이미 진행 중이라 아직 돌려줄 배너가 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_028", "message": "요약을 만드는 중입니다. 잠시 후 다시 확인해 주세요.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "502", description = "에이전트 서버에 닿지 못함(AGENT_003) / 응답을 해석하지 못함(AGENT_007)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_003", "message": "에이전트 서버에 연결하지 못했습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "504", description = "제한 시간 안에 응답이 오지 않음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_008", "message": "에이전트 응답 시간이 초과되었습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<ProjectSummaryResponse> refreshSummary(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "대상 프로젝트 id", example = "3") Long projectId);
}
