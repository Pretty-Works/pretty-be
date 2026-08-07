package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.res.ProjectSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "프로젝트 AI 요약", description = """
        프로젝트 탭 상단 AI 요약 배너 API.

        [이게 뭔가]
        프로젝트 하위 탭 4종(overview·board·budget·meeting) 각각의 상단에 뜨는 배너입니다.
        배너 한 장은 headline(제목 줄) + detail[](불릿) + stats[](칩)으로 이뤄집니다.

        [누가 만드나]
        문장도 숫자도 BE가 만들지 않습니다. BE는 재료를 모아 에이전트 서버(FastAPI)에 넘기고,
        돌아온 배너를 해석하지 않고 저장했다가 그대로 돌려주는 게이트입니다.
        그래서 LLM팀이 필드를 추가하면 BE 배포 없이 프론트에 바로 도착합니다 —
        summaries[] 안쪽은 이 문서의 스키마가 아니라 예시로 읽으세요.

        [프론트가 쓸 것은 조회(GET) 하나입니다]
        projectId만 넘기면 됩니다. 요약이 아직 없으면 조회가 그 자리에서 만들어서 돌려주므로,
        프론트가 "요약이 있나 없나"를 판단해 생성 API를 따로 부를 일이 없습니다.
        갱신(POST)은 "방금 데이터를 바꿨으니 배너도 다시"를 명시적으로 요구할 때만 쓰는 선택 API입니다.

        [언제 만들어지나]
        ① 매일 새벽 배치가 진행중·보류 프로젝트를 전부 다시 만듭니다. 평소에는 이걸로 이미 준비돼 있습니다.
        ② 조회했는데 저장된 게 하나도 없으면 그 자리에서 만듭니다(새 프로젝트·배치 실패 후).
        ③ 갱신 API를 부르면 만듭니다.
        """)
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
                    **프론트가 부르는 것은 이것 하나입니다. projectId만 넘기면 됩니다.**

                    [요청]
                    - section: 한 장만 필요할 때 지정합니다(overview·board·budget·meeting).
                      생략하면 4장을 overview → board → budget → meeting 순서로 전부 돌려줍니다.
                      **한 번에 4장을 받아 탭 전환 때 골라 쓰는 편이 낫습니다** — 탭마다 부를 이유가 없습니다.

                    [응답]
                    - generatedAt: 이 배너를 만든 시각. 화면에 "○○ 기준"으로 쓰세요.
                    - summaries[]: 배너 원문 그대로. section으로 어느 탭의 것인지 구분합니다.

                    [속도]
                    보통은 저장된 것을 읽기만 하므로 즉시 응답합니다(LLM 호출 없음).
                    다만 **저장된 요약이 하나도 없으면 그 자리에서 만들기 때문에 수 초가 걸립니다.**
                    새로 만든 프로젝트나 새벽 배치가 실패한 뒤가 여기 해당합니다.
                    그래서 배너 자리에는 스켈레톤을 한 번 띄워 두는 편이 안전합니다.

                    [만들지 못했을 때]
                    에러가 아니라 `summaries: []`, `generatedAt: null`입니다 — 에이전트 서버가 죽어 있어도
                    이 API는 200을 돌려줍니다. 배너는 부가 정보라 프로젝트 화면 전체를 막으면 안 되기 때문입니다.
                    프론트는 배너를 그리지 않고 넘어가면 됩니다.
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
                    **필수가 아닙니다.** 화면 진입은 조회(GET)만으로 끝납니다 — 요약이 없으면 조회가 알아서 만듭니다.
                    이 API는 "저장된 배너가 있지만 지금 것이 아니다"를 프론트가 아는 순간에만 씁니다.

                    재료를 모아 에이전트 서버에 넘기고, 새로 만든 배너 4장을 저장한 뒤 그대로 돌려줍니다.
                    **응답이 올 때까지 수 초가 걸립니다.**

                    [언제 부르나]
                    - 할 일 완료·마일스톤 체크·지출 등록처럼 배너 숫자를 바꾸는 작업을 마친 직후
                    - 사용자가 배너의 '새로고침'을 눌렀을 때
                    응답을 기다리게 하지 말고 쏜 뒤 도착하면 배너만 갈아 끼우세요.

                    [연타해도 되나]
                    됩니다. 마지막 생성으로부터 몇 분(agent.summary.min-refresh-interval-minutes,
                    기본 10분)이 지나지 않았으면 LLM을 부르지 않고 저장된 배너를 즉시 돌려줍니다.
                    같은 프로젝트를 여러 사람이 동시에 열어도 실제 호출은 한 번만 나갑니다.

                    [조회와 달리 실패를 알립니다]
                    사용자가 갱신을 요구한 이상 실패는 알려야 하므로 502·504가 그대로 나갑니다.
                    저장된 이전 배너는 지워지지 않으니, 화면은 그대로 두고 "지금은 갱신할 수 없어요" 정도만 알리면 됩니다.
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
