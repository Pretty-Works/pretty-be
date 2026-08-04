package HK.PrettyWorks_BE.user.controller;

import HK.PrettyWorks_BE.user.dto.res.MyProfileResponse;
import HK.PrettyWorks_BE.user.dto.res.UserSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

// 사용자 API의 Swagger 문서 전용 인터페이스. 컨트롤러(UserController)가 implements 한다.
@Tag(name = "사용자", description = "내 정보 조회와 사용자 선택 자동완성 API.")
public interface UserApi {

    @Operation(
            summary = "내 정보 조회",
            description = """
                    로그인한 본인의 기본 정보를 조회합니다. 토큰의 userId로 조회하므로 경로에 사용자 ID를 받지 않습니다.

                    앱 진입 시 한 번 호출해 상단바 표시와 화면 요소 노출 판단에 재사용하세요.
                    부서·직급 변경은 다음 진입 때 반영되며 실시간으로 갱신되지 않습니다.

                    [canCreateProject] 프로젝트 생성 버튼 노출 여부입니다. 판정 규칙("팀장 이상 또는 PM 부서")에 쓰는
                    직급 서열이 서버에만 있어 클라이언트가 같은 계산을 할 수 없으므로 결과를 내려줍니다.
                    ⚠️ **화면 표시용 힌트입니다.** 실제 권한은 생성 요청 시 서버가 다시 판정하므로(PROJECT_001),
                    캐시된 값이 낡아 버튼이 보이더라도 생성은 거부될 수 있습니다.

                    [department] MANAGEMENT_SUPPORT 경영지원 / HR 인사 / FINANCE 재무회계 / SALES 영업 /
                    PLANNING 사업기획 / CONSULTING 컨설팅 / PM 프로젝트관리 / FRONTEND 프론트엔드개발 /
                    BACKEND 백엔드개발 / DEVOPS 데브옵스 / DATA 데이터관리 / INFRA 인프라운영 / SECURITY 정보보안 / QA 품질보증

                    [position] 낮은 직급부터 — STAFF 사원 / SENIOR 선임 / PART_LEADER 파트장 / TEAM_LEADER 팀장 /
                    EXECUTIVE 임원 / VICE_PRESIDENT 부사장 / PRESIDENT 사장

                    ⚠️ 이 API로 알 수 없는 것
                    - **프로젝트 수정·상태변경·마일스톤 토글 권한** — 프로젝트마다 다릅니다(같은 사람이 A에선 PM, B에선 일반 참여자).
                      프로젝트 상세 조회의 owner·members[].role로 판단하세요.
                    - **주간 Task 보드의 '내 팀'** — 이미 서버가 계산해 groups[].isMine으로 내려줍니다. 부서를 비교하지 마세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<MyProfileResponse> getMyProfile(Long userId);

    @Operation(
            summary = "사용자 이름 자동완성 검색",
            description = """
                    프로젝트 구성원·일정 참가자 선택 드롭다운에서 사용할 사용자를 이름 일부로 검색합니다.
                    로그인한 사용자 본인은 검색 결과에서 제외합니다.
                    재직자(ACTIVE)와 휴직자(ON_LEAVE)를 함께 조회하며 퇴사자(RESIGNED)는 제외합니다.
                    검색어는 최대 20자이며 한글·영문과 단어 사이 공백만 사용할 수 있습니다.
                    이름·부서·직급·재직 상태만 반환하며 최대 20건까지 조회할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공 (검색 결과가 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "검색어·limit 범위 위반 또는 퇴사한 사용자의 요청")
    })
    ResponseEntity<List<UserSearchResponse>> searchUsers(
            Long requesterId,
            @Parameter(description = "이름 부분 검색어", example = "예린") String keyword,
            @Parameter(description = "최대 검색 결과 수 (1~20)", example = "10") int limit
    );
}
