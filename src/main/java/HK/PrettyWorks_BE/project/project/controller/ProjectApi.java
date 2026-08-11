package HK.PrettyWorks_BE.project.project.controller;

import HK.PrettyWorks_BE.global.base.PageResponse;
import HK.PrettyWorks_BE.project.member.dto.res.ProjectMemberResponse;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectRequest;
import HK.PrettyWorks_BE.project.project.dto.req.ProjectStatusRequest;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectDetailResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectListResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectResponse;
import HK.PrettyWorks_BE.project.project.dto.res.ProjectStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

// 프로젝트 API의 Swagger 문서 전용 인터페이스. 컨트롤러(ProjectController)가 implements 한다.
//
// 필수 여부·길이 제한·타입은 스키마에 이미 나오므로 적지 않는다.
// 코드 값의 의미, 모르면 사고가 나는 계약, 도메인 고유 에러만 남긴다.
// 성공 응답의 BaseResponse 래핑과 공통 401·500은 SwaggerConfig의 customizer가 붙인다.
@Tag(name = "프로젝트", description = "프로젝트 생성·조회·수정·상태변경 API. 참여자·마일스톤을 함께 관리한다.")
public interface ProjectApi {

    @Operation(
            summary = "프로젝트 생성",
            description = """
                    호출자가 오너가 되어 프로젝트를 만듭니다. 참여자·마일스톤을 함께 등록합니다.
                    직급이 팀장(TEAM_LEADER) 이상이거나 부서가 PM인 사용자만 생성할 수 있습니다.

                    [budget] 원 단위 정수. 미입력이나 0은 '예산 제한 없음'이며 소수점은 넣지 않습니다.
                    [members] 오너는 서버가 자동 등록합니다. 퇴사자는 넣을 수 없고(USER_003) 휴직자는 가능합니다.
                    호출자 본인이 퇴사자여도 같은 코드로 차단됩니다.
                    [milestones] targetDate와 goal이 **둘 다** 있어야 합니다(PROJECT_016).
                    milestoneId를 실어 보내도 무시됩니다 — 새 프로젝트라 전부 신규입니다.
                    [role] 직무 표시용 문자열입니다. 권한과 무관합니다 — 수정 권한은 오너이거나 부서가 PM인 경우입니다.

                    Idempotency-Key 헤더에 UUID를 담아 보내면 연타·재시도해도 프로젝트가 두 개 생기지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400",
                    description = "입력값 검증 실패 / 기간 오류(PROJECT_003) / 마일스톤 오류(PROJECT_015·PROJECT_016)"
                            + " / 퇴사자(USER_003) — 호출자 본인이거나 참여자로 지정한 사람"),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음(PROJECT_001) — 팀장 미만이면서 PM 부서도 아님"),
            @ApiResponse(responseCode = "404", description = "참여자로 지정한 사용자를 찾을 수 없음(PROJECT_002)"),
            @ApiResponse(responseCode = "409", description = "같은 멱등 키로 다른 내용의 요청이 접수됨(REQUEST_028)")
    })
    ResponseEntity<ProjectResponse> create(Long ownerId, String idempotencyKey, ProjectRequest request);

    @Operation(
            summary = "프로젝트 목록 조회",
            description = """
                    내가 참여중인 프로젝트를 페이지 단위로 조회합니다.

                    [status] ONGOING 진행중(기본) / HOLDING 보류 / COMPLETED 완료 / DROPPED 중단 / ALL 전체
                    - ARCHIVED(보관)는 삭제에 해당해 조회할 수 없습니다(PROJECT_018).

                    [정렬] 서버 고정이며 클라이언트가 바꿀 수 없습니다.

                    ⚠️ progress는 진행률이 아니라 **기간 경과율(%)** 입니다. 작업 진척도와 무관합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과가 없으면 content는 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "허용되지 않는 status 값(PROJECT_018) 또는 페이지 파라미터 범위 위반")
    })
    ResponseEntity<PageResponse<ProjectListResponse>> getMyProjects(
            Long userId, String status, String keyword, int page, int size);

    @Operation(
            summary = "프로젝트 상세 조회",
            description = """
                    수정 화면 진입용 조회입니다. 참여중인 멤버면 누구나 조회할 수 있습니다.

                    ⚠️ 응답의 version을 보관했다가 수정(PUT) 시 X-Resource-Version 헤더로 되돌려 보내야 합니다.

                    [owner] 오너는 members와 분리해 내려주며 members에는 포함되지 않습니다.
                    [members] 참여중인 참여자만. 프로젝트를 떠난 사람은 나오지 않습니다.
                    [owner·members[].department·position] 참여자 카드를 "이름 · 부서"로 표시할 때 씁니다.
                    사용자 검색(`GET /users/search`)과 **같은 코드 값**이라, 기존 참여자와 방금 검색해 추가한
                    참여자를 같은 방식으로 그리면 됩니다. 한글 라벨 변환은 화면이 담당합니다.
                    [members[].role] 프로젝트 내 직무 역할(자유 문자열)입니다. 회사 직급인 position과 다릅니다.
                    [milestones[].milestoneId] **영구 식별자입니다.** 수정(PUT) 요청에 그대로 실어 보내야 완료 상태가 보존됩니다.
                    [milestones[].done] 완료 여부. 사용자가 완료된 마일스톤을 지우려 할 때 확인을 띄우는 데 쓰세요.
                    [budget] 원 단위 정수. 0은 '예산 제한 없음'.
                    [progress] 기간 경과율(%). 작업 진척도가 아닙니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "MEMBER_001", "message": "해당 프로젝트에 참여하고 있지 않습니다.", "result": null }
                                    """))),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "PROJECT_004", "message": "프로젝트를 찾을 수 없습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<ProjectDetailResponse> getDetail(Long userId, Long projectId);

    @Operation(
            summary = "프로젝트 참여자 조회",
            description = """
                    참여중인 재직자를 반환합니다. **참여자 명단 표시와 사용자 선택 자동완성이 같은 API**를 씁니다.
                    호출자가 해당 프로젝트의 참여중 멤버여야 합니다(MEMBER_001).

                    [명단 용도] 파라미터 없이 호출하세요. 회의 참석자 선택, 할 일 담당자 목록 등에 씁니다.
                    [자동완성 용도] `keyword`로 이름 일부를 넘기고 `excludeSelf=true`를 붙이세요.
                    이미 참여 중인 본인이 "참여자 추가" 후보로 뜨는 것을 막습니다.

                    [정렬] 오너가 항상 맨 앞, 그 뒤는 이름순입니다. 서버 고정입니다.
                    [범위] 휴직자(ON_LEAVE)는 포함하고 프로젝트를 떠난 멤버·퇴사자는 제외합니다.
                    사내 사용자 검색(`GET /users/search`)과 재직 기준이 같습니다.
                    [status] 재직 상태입니다. 휴직자를 화면에 표시할 때 쓰세요.
                    [role] 프로젝트 내 직무 역할(자유 문자열)입니다. 회사 직급인 position과 다릅니다.
                    [isOwner] 오너 여부. 명단에서 오너 배지를 붙일 때 씁니다.

                    keyword는 최대 20자이며 한글·영문과 단어 사이 공백만 사용할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과가 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "keyword·size 범위 위반 또는 퇴사한 사용자의 요청(USER_003)"),
            @ApiResponse(responseCode = "403", description = "해당 프로젝트의 참여중 멤버가 아님(MEMBER_001)"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)")
    })
    ResponseEntity<List<ProjectMemberResponse>> getMembers(
            Long requesterId,
            Long projectId,
            @Parameter(description = "이름 부분 검색어. 비우면 전체", example = "하늘") String keyword,
            @Parameter(description = "요청자 본인 제외 여부. 자동완성은 true", example = "false") boolean excludeSelf,
            @Parameter(description = "최대 조회 수 (1~100)", example = "50") int size
    );

    @Operation(
            summary = "프로젝트 수정",
            description = """
                    오너 또는 부서가 PM인 참여자만 수정할 수 있습니다. 완료·보관된 프로젝트는 수정할 수 없습니다(PROJECT_020).

                    ⚠️ **전체 상태를 보냅니다(PUT).** members·milestones에서 빠진 항목은 삭제(참여자는 탈퇴 처리)되므로,
                    변경분만 보내면 나머지가 모두 사라집니다.

                    [마일스톤] milestoneId 기준으로 대조합니다. id가 있으면 기존 항목을 갱신하고, 없으면(null) 새로 만듭니다.
                    - **id를 실어 보내면 목표일·내용을 고쳐도 완료 상태가 유지**됩니다. 생략하면 새 마일스톤으로 취급되어 완료 표시가 사라집니다.
                    - 이 프로젝트에 없는 id를 보내면 404(PROJECT_022)입니다. 다른 프로젝트의 id도 마찬가지입니다.
                    - ⚠️ **완료 체크된 마일스톤도 요청에서 빠지면 삭제**되며 복구할 수 없습니다. 상세 조회의 done을 보고 확인을 띄우세요.

                    ⚠️ X-Resource-Version 헤더 필수 — 상세 조회에서 받은 version을 그대로 넣습니다.
                    409(REQUEST_029)면 상세 조회를 다시 해 최신 내용을 보여준 뒤 재시도하세요.
                    (마일스톤 완료 토글은 version을 올리지 않으므로 이 저장을 방해하지 않습니다)

                    새 기간을 벗어나는 할 일·지출·회의록이 남아 있으면 기간을 줄일 수 없습니다(PROJECT_021).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400",
                    description = "입력값 검증 실패 / 기간 오류(PROJECT_003) / 마일스톤 오류(PROJECT_015·PROJECT_016)"
                            + " / 퇴사자(USER_003) — 호출자 본인이거나 참여자로 지정한 사람"),
            @ApiResponse(responseCode = "403", description = "오너도 PM도 아님(PROJECT_005)"),
            @ApiResponse(responseCode = "404",
                    description = "프로젝트(PROJECT_004) / 참여자(PROJECT_002) / 마일스톤(PROJECT_022)을 찾을 수 없음"),
            @ApiResponse(responseCode = "409",
                    description = "완료·보관된 프로젝트(PROJECT_020) / 다른 사용자가 먼저 수정함(REQUEST_029) / 기간 축소 불가(PROJECT_021)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_029", "message": "다른 사용자가 먼저 수정했습니다. 최신 내용을 다시 불러온 뒤 시도해 주세요.", "result": null }
                                    """)))
    })
    ResponseEntity<ProjectResponse> update(Long userId, Long projectId, Long version, ProjectRequest request);

    @Operation(
            summary = "프로젝트 상태 변경",
            description = """
                    진행 상태만 변경합니다. 오너 또는 부서가 PM인 참여자만 가능합니다(PROJECT_017).

                    [status] ONGOING 진행중 / HOLDING 보류 / COMPLETED 완료 / DROPPED 중단 / ARCHIVED 보관(소프트 삭제)

                    ⚠️ 완료(COMPLETED)·보관(ARCHIVED)은 되돌릴 수 없습니다(PROJECT_019). 진행중 ↔ 보류는 자유롭게 오갑니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 상태 값(PROJECT_018) / 퇴사한 사용자(USER_003)"),
            @ApiResponse(responseCode = "403", description = "오너도 PM도 아님(PROJECT_017)"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음(PROJECT_004)"),
            @ApiResponse(responseCode = "409", description = "완료·중단된 프로젝트를 되돌리려 함(PROJECT_019)")
    })
    ResponseEntity<ProjectStatusResponse> changeStatus(Long userId, Long projectId, ProjectStatusRequest request);
}
