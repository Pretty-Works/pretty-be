package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.project.project.dto.req.MilestoneStatusRequest;
import HK.PrettyWorks_BE.project.project.dto.res.MilestoneListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 마일스톤 API의 Swagger 문서 전용 인터페이스. 컨트롤러(MilestoneController)가 implements 한다.
//
// 필수 여부·길이 제한·타입은 스키마에 이미 나오므로 적지 않는다.
// 성공 응답의 BaseResponse 래핑과 공통 401·500은 SwaggerConfig의 customizer가 붙인다.
@Tag(name = "마일스톤", description = "프로젝트 개요 화면의 마일스톤 완료율 카드 조회·완료 토글 API. 추가·수정·삭제는 프로젝트 수정 API가 담당한다.")
public interface MilestoneApi {

    @Operation(
            summary = "마일스톤 목록 조회",
            description = """
                    참여중인 멤버가 프로젝트의 마일스톤과 완료 집계를 조회합니다. 페이지네이션이 없어 전부 내려갑니다.

                    [정렬] 목표일 오름차순, 같으면 등록 순. 서버 고정입니다.
                    [nextMilestone] 목표 마일스톤 = **미완료 중 목표일이 가장 이른 것**. 전부 완료했거나 마일스톤이 없으면 null입니다.
                    [done] 저장된 완료 시각의 유무에서 파생합니다.

                    ⚠️ completionRate는 **완료 건수 기준이지 날짜 기준이 아닙니다.** 목표일이 지났어도 체크하지 않았으면
                    미완료로 셉니다. 프로젝트 목록의 progress(기간 경과율)와 의도적으로 다른 지표이며,
                    둘을 나란히 보면 "기간은 70% 지났는데 마일스톤은 40%"처럼 지연을 읽어낼 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (마일스톤이 없으면 빈 배열 + 집계 0)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)")
    })
    ResponseEntity<MilestoneListResponse> getMilestones(Long userId, Long projectId);

    @Operation(
            summary = "마일스톤 완료 토글",
            description = """
                    완료 여부만 변경합니다. 목표일·내용 수정과 추가·삭제는 프로젝트 수정 API에서 다룹니다.
                    오너 또는 역할이 "PM"인 참여자만 가능합니다(PROJECT_005) — 완료 체크가 곧 완료율이라
                    참여자 누구나 바꾸면 지표를 신뢰할 수 없기 때문입니다.

                    같은 값을 여러 번 보내도 결과가 같습니다(멱등). 이미 완료인 것에 done=true를 다시 보내도
                    **최초 완료 시각이 유지**되어 완료 시점이 뒤로 밀리지 않습니다. result는 null입니다.

                    목표일과 완료 여부는 서로 무관합니다. 목표일이 지나도 자동 완료되지 않고, 미리 완료 처리할 수도 있습니다.

                    이 API는 프로젝트의 version을 올리지 않습니다. 열려 있는 수정 화면의 저장을 방해하지 않으며,
                    프로젝트 수정은 완료 시각을 건드리지 않으므로 체크한 내용이 덮어쓰이지도 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공 (이미 같은 상태여도 성공)"),
            @ApiResponse(responseCode = "400", description = "done 미입력(REQUEST_001) / 퇴사한 사용자(USER_003)"),
            @ApiResponse(responseCode = "403", description = "오너도 PM도 아님(PROJECT_005)"),
            @ApiResponse(responseCode = "404",
                    description = "프로젝트를 찾을 수 없음(PROJECT_004) / 마일스톤이 없거나 이 프로젝트의 것이 아님(PROJECT_022)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_022", "message": "마일스톤을 찾을 수 없습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "409", description = "완료·보관된 프로젝트(PROJECT_020)")
    })
    Void toggleStatus(Long userId, Long projectId, Long milestoneId, MilestoneStatusRequest request);
}
