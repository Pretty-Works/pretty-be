package HK.PrettyWorks_BE.task.controller;

import HK.PrettyWorks_BE.task.dto.req.TaskRequest;
import HK.PrettyWorks_BE.task.dto.req.TaskStatusRequest;
import HK.PrettyWorks_BE.task.dto.res.TaskHomeResponse;
import HK.PrettyWorks_BE.task.dto.res.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 할 일 API의 Swagger 문서 전용 인터페이스. 컨트롤러(TaskController)가 implements 한다.
//
// 필수 여부·길이 제한·타입은 스키마에 이미 나오므로 적지 않는다.
// 코드 값의 의미, 모르면 사고가 나는 계약, 도메인 고유 에러만 남긴다.
// 성공 응답의 BaseResponse 래핑과 공통 401·500은 SwaggerConfig의 customizer가 붙인다.
@Tag(name = "할 일", description = "개인·프로젝트 할 일 생성·수정·삭제·완료 토글과 홈 조회 API.")
public interface TaskApi {

    @Operation(
            summary = "할 일 생성",
            description = """
                    작성자는 토큰에서 가져오므로 요청에 담지 않습니다.

                    [projectId] null이면 개인 할 일, 값이 있으면 그 프로젝트의 할 일입니다.
                    프로젝트 할 일은 참여중인 멤버만 만들 수 있고(MEMBER_001),
                    마감일이 프로젝트 기간 안이어야 하며(TASK_007), 완료·보관된 프로젝트에는 만들 수 없습니다(PROJECT_020).
                    개인 할 일에는 이 세 제약이 모두 적용되지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 마감일이 프로젝트 기간을 벗어남(TASK_007)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)"),
            @ApiResponse(responseCode = "409", description = "완료·보관된 프로젝트(PROJECT_020)")
    })
    ResponseEntity<TaskResponse> create(Long userId, TaskRequest request);

    @Operation(
            summary = "할 일 수정",
            description = """
                    작성자 본인만 수정할 수 있습니다(TASK_004). 프로젝트 오너라도 남의 할 일은 수정할 수 없습니다.

                    ⚠️ **전체 상태를 보냅니다(PUT).** projectId를 빼면 개인 할 일로 바뀌고,
                    다른 projectId를 넣으면 그 프로젝트로 옮겨집니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 / 마감일이 프로젝트 기간을 벗어남(TASK_007)"),
            @ApiResponse(responseCode = "403", description = "작성자가 아님(TASK_004) / 프로젝트 참여자가 아님(MEMBER_001)"),
            @ApiResponse(responseCode = "404", description = "할 일(TASK_003) 또는 프로젝트(PROJECT_004)를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "완료·보관된 프로젝트(PROJECT_020)")
    })
    ResponseEntity<TaskResponse> update(Long userId, Long taskId, TaskRequest request);

    @Operation(
            summary = "할 일 삭제",
            description = """
                    작성자 본인만 삭제할 수 있습니다(TASK_005).

                    ⚠️ 소프트 삭제가 아니라 **완전 삭제**입니다. 복구할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "작성자가 아님(TASK_005)"),
            @ApiResponse(responseCode = "404", description = "할 일을 찾을 수 없음(TASK_003)")
    })
    ResponseEntity<TaskResponse> delete(Long userId, Long taskId);

    @Operation(
            summary = "할 일 완료 토글",
            description = """
                    작성자 본인만 변경할 수 있습니다(TASK_004). done=true면 완료, false면 완료 취소입니다.
                    같은 값을 여러 번 보내도 결과가 같습니다(멱등). result는 null입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "done 미입력"),
            @ApiResponse(responseCode = "403", description = "작성자가 아님(TASK_004)"),
            @ApiResponse(responseCode = "404", description = "할 일을 찾을 수 없음(TASK_003)")
    })
    ResponseEntity<Void> toggleStatus(Long userId, Long taskId, TaskStatusRequest request);

    @Operation(
            summary = "할 일 홈 조회",
            description = """
                    본인 할 일을 프로젝트별로 묶어 반환합니다. 페이지네이션이 없어 한 번에 전부 내려갑니다.

                    [범위] 미완료 전부 + 최근 3일 이내에 완료한 것. 보관(ARCHIVED)된 프로젝트의 할 일은 빠집니다.
                    [groups] 개인 할 일 그룹은 projectId·projectName이 null이며 항상 마지막에 옵니다.
                    [dDay] 오늘~마감일 일수. 음수면 마감이 지난 것입니다.
                    [done] 저장된 값이 아니라 완료 시각 유무에서 파생한 값입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (할 일이 없으면 groups는 빈 배열)")
    })
    ResponseEntity<TaskHomeResponse> getTaskHome(Long userId);
}
