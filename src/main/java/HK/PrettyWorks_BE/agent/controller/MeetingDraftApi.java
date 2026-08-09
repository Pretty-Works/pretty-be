package HK.PrettyWorks_BE.agent.controller;

import HK.PrettyWorks_BE.agent.dto.res.MeetingDraftResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "회의록 초안 생성", description = "회의 기록(txt)으로 회의록 작성 폼에 채울 값을 만드는 API")
public interface MeetingDraftApi {

    @Operation(
            summary = "회의록 초안 생성",
            description = """
                    회의 기록 txt를 올려 회의록 작성 폼에 채울 값을 받습니다.

                    * 요청은 multipart/form-data 이며 파트는 file 하나뿐입니다. txt(UTF-8)만, 녹취록은 30,000자까지입니다.
                    * 응답 필드를 폼의 제목·회의일·장소·목적·주요 내용·후속 조치·참석자에 그대로 넣으면 됩니다.
                      저장 규격에 맞게 손질해 내려주므로 그대로 저장 API로 보내도 됩니다.
                    * 근거가 없는 칸은 null이며 전부 null도 정상입니다. 비워 두면 되고 실패로 안내하지 마세요.
                    * 수십 초 걸리고 최대 2분까지 기다립니다. 한 사용자당 동시에 한 건만 만듭니다(재호출은 429).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초안 생성 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MeetingDraftResponse.class),
                            examples = {
                                    @ExampleObject(name = "전부 찾은 경우", value = """
                                            {
                                              "errorCode": null,
                                              "message": "SUCCESS",
                                              "result": {
                                                "title": "스프린트 리뷰",
                                                "meetingDate": "2026-08-05",
                                                "location": "회의실 A",
                                                "purpose": "API 개발 진행 상황 점검",
                                                "content": "· API 개발 진행률 확인\\n· 일정 조정 논의",
                                                "followUp": "· API 명세 작성 — 이하늘, 8월 8일",
                                                "attendeeUserIds": [1, 2]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "근거가 부족한 경우", value = """
                                            {
                                              "errorCode": null,
                                              "message": "SUCCESS",
                                              "result": {
                                                "title": "주간 회의",
                                                "meetingDate": null,
                                                "location": null,
                                                "purpose": null,
                                                "content": "· 진행 상황 공유",
                                                "followUp": null,
                                                "attendeeUserIds": []
                                              }
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "400",
                    description = """
                            txt가 아님(AGENT_029) / UTF-8이 아니거나 읽지 못함(AGENT_032) /
                            내용이 비어 있음(AGENT_033) / 30,000자 초과(AGENT_023) /
                            완료·보관된 프로젝트(MEETING_008) / file 파트 누락(REQUEST_026)
                            """,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_023", "message": "녹취록이 너무 깁니다. 30,000자 이내로 줄여 주세요.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "403", description = "이 프로젝트의 참여중 멤버가 아님 / 퇴사자",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 projectId",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "413", description = "업로드 파일이 멀티파트 상한(20MB)을 넘음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_027", "message": "업로드 파일 크기가 허용된 한도를 초과했습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "429", description = "이 사용자의 초안 생성이 이미 진행 중",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_024", "message": "초안을 만드는 중입니다. 잠시만 기다려 주세요.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "502", description = "에이전트 서버에 닿지 못함(AGENT_003) / 응답을 해석하지 못함(AGENT_007)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_003", "message": "에이전트 서버에 연결하지 못했습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "504", description = "제한 시간(2분) 안에 응답이 오지 않음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AGENT_008", "message": "에이전트 응답 시간이 초과되었습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<MeetingDraftResponse> createMeetingDraft(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "회의록을 쓸 프로젝트 id", example = "3") Long projectId,
            @Parameter(description = "회의 기록 txt 파일 (UTF-8, 30,000자 이내)")
            MultipartFile file);
}
