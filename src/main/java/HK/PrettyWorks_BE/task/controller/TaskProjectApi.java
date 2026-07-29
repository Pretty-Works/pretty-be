package HK.PrettyWorks_BE.task.controller;

import HK.PrettyWorks_BE.task.dto.res.TaskProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 프로젝트 인원 보드 API의 Swagger 문서 전용 인터페이스. 컨트롤러(TaskProjectController)가 implements 한다.
// 할 일 도메인이지만 URL과 화면(프로젝트 상세)이 달라 TaskApi와 분리한다.
@Tag(name = "프로젝트 인원 보드", description = "프로젝트 상세 화면의 주 단위 팀별 할 일 현황 조회 API.")
public interface TaskProjectApi {

    @Operation(
            summary = "프로젝트 인원 할 일 보드 조회",
            description = """
                    참여중인 멤버가 주 단위로 팀별 할 일과 완료율을 봅니다. 본인 것뿐 아니라 팀 전체를 조회합니다.

                    [weekOffset] 0 이번 주(기본) / -1 지난 주 / 1 다음 주. 주는 월요일~일요일입니다.
                    [범위] 해당 주에 마감인 할 일 + **이전 주에서 넘어온 미완료 할 일**(마감이 지나도 계속 보입니다).
                    [groups] 조회자 부서가 먼저 오고 나머지는 부서명 가나다순입니다. isMine으로 구분할 수 있습니다.
                    [team] 담당자의 현재 부서에서 파생합니다. 할 일에 부서를 저장하지 않으므로 담당자가 이동하면 집계도 옮겨갑니다.
                    [rate] 완료율(%) 내림. 할 일이 없으면 0입니다.
                    [overdue] 미완료이면서 마감이 지난 것.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (할 일이 없으면 groups는 빈 배열)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)")
    })
    ResponseEntity<TaskProjectResponse> getTaskProject(Long userId, Long projectId, int weekOffset);
}
