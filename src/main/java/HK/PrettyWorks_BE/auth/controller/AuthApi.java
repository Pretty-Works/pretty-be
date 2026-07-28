package HK.PrettyWorks_BE.auth.controller;

import HK.PrettyWorks_BE.auth.dto.req.LoginRequest;
import HK.PrettyWorks_BE.auth.dto.req.ReissueRequest;
import HK.PrettyWorks_BE.auth.dto.req.SignupRequest;
import HK.PrettyWorks_BE.auth.dto.res.JwtTokenResponse;
import HK.PrettyWorks_BE.auth.dto.res.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

// 인증 API의 Swagger 문서 전용 인터페이스. 컨트롤러(AuthController)가 implements 한다.
//
// 필수 여부·길이 제한·타입은 스키마에 이미 나오므로 적지 않는다.
// 코드 값의 의미, 모르면 사고가 나는 계약, 도메인 고유 에러만 남긴다.
// 성공 응답의 BaseResponse 래핑과 공통 401·500은 SwaggerConfig의 customizer가 붙인다.
@Tag(name = "인증", description = "회원가입·로그인·토큰 재발급·로그아웃 API. 로그인은 기기별로 세션이 따로 생긴다.")
public interface AuthApi {

    @Operation(
            summary = "회원가입",
            description = """
                    임직원 정보로 신규 계정을 생성합니다. 토큰이 필요 없습니다.

                    [gender] MALE 남성 / FEMALE 여성

                    [department] MANAGEMENT_SUPPORT 경영지원 / HR 인사 / FINANCE 재무회계 / SALES 영업 /
                    PLANNING 사업기획 / CONSULTING 컨설팅 / PM 프로젝트관리 / FRONTEND 프론트엔드개발 /
                    BACKEND 백엔드개발 / DEVOPS 데브옵스 / DATA 데이터관리 / INFRA 인프라운영 / SECURITY 정보보안 / QA 품질보증

                    [position] 낮은 직급부터 — STAFF 사원 / SENIOR 선임 / PART_LEADER 파트장 / TEAM_LEADER 팀장 /
                    EXECUTIVE 임원 / VICE_PRESIDENT 부사장 / PRESIDENT 사장
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일(AUTH_001) 또는 사번(AUTH_002)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AUTH_002", "message": "이미 사용 중인 사번입니다.", "result": null }
                                    """)))
    })
    ResponseEntity<SignupResponse> signup(SignupRequest request);

    @Operation(
            summary = "로그인",
            description = """
                    사번·비밀번호로 access/refresh 토큰을 발급합니다. 토큰이 필요 없습니다.
                    accessToken은 이후 요청에 `Authorization: Bearer {accessToken}`으로 보냅니다.

                    로그인할 때마다 기기별 세션이 생겨 여러 기기에서 동시에 쓸 수 있습니다.
                    최대 세션 수를 넘기면 가장 오래된 세션이 자동 종료됩니다.

                    ⚠️ 퇴사자도 비밀번호 오류와 **같은 401(AUTH_003)** 로 응답합니다. 사번 존재 여부가 새지 않도록 한 것이라
                    "퇴사한 계정입니다" 같은 안내는 만들 수 없습니다. 휴직자는 로그인 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "사번·비밀번호 미입력"),
            @ApiResponse(responseCode = "401", description = "사번·비밀번호 불일치 또는 퇴사한 계정",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "AUTH_003", "message": "사번 또는 비밀번호가 올바르지 않습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<JwtTokenResponse> login(LoginRequest request, String userAgent);

    @Operation(
            summary = "토큰 재발급 (RTR)",
            description = """
                    refresh 토큰을 검증해 access/refresh 토큰을 **둘 다** 새로 발급합니다. 세션(기기)은 유지됩니다.
                    access token은 보지 않으므로 만료된 토큰이 헤더에 남아 있어도 됩니다.

                    ⚠️ 응답의 새 refreshToken으로 반드시 교체하세요. 이미 쓴 refresh 토큰이 다시 오면
                    **도난으로 판단해 해당 기기의 세션을 통째로 종료**하며 재로그인이 필요합니다.
                    재발급 요청이 동시에 두 번 나가지 않도록 하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401",
                    description = "토큰 없음·만료(REQUEST_024) / 불일치(REQUEST_025) / 재사용 감지로 세션 종료 / 퇴사 처리된 계정",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "errorCode": "REQUEST_024", "message": "리프레시 토큰이 만료되었거나 존재하지 않습니다.", "result": null }
                                    """)))
    })
    ResponseEntity<JwtTokenResponse> reissue(ReissueRequest request, String userAgent);

    @Operation(
            summary = "로그아웃",
            description = """
                    요청을 보낸 **그 기기의 세션만** 종료합니다. 다른 기기의 로그인은 유지됩니다.
                    refresh 토큰이 폐기되고 남은 access token도 즉시 차단됩니다.

                    이미 로그아웃된 세션으로 다시 호출해도 성공합니다(멱등). result는 null입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공 (이미 로그아웃된 경우에도 성공)")
    })
    Void logout(@Parameter(hidden = true) Authentication authentication);
}
