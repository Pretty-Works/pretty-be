package HK.PrettyWorks_BE.auth.constant;

// 인증/인가 코드에서 반복해서 쓰는 문자열을 한 곳에 모아둔 상수 클래스입니다.
public class AuthConstant {
    // Authorization 헤더에서 JWT 앞에 붙는 표준 접두어입니다.
    public static final String BEARER_PREFIX = "Bearer ";
    // 클라이언트가 JWT를 담아 보내는 HTTP 헤더 이름입니다.
    public static final String AUTHORIZATION_HEADER = "Authorization";
    // JWT payload에서 사용자 ID를 저장하는 claim 이름입니다.
    public static final String USER_ID_CLAIM_NAME = "uid";
    // access/refresh 토큰을 구분하기 위해 JWT payload에 저장하는 claim 이름입니다.
    public static final String TOKEN_TYPE_CLAIM_NAME = "type";
    // 로그인 세션(기기)을 식별하는 claim 이름입니다. 토큰이 회전해도 같은 세션이면 값이 유지됩니다.
    // OIDC가 세션 식별용으로 정의한 표준 claim 이름과 동일하게 맞췄습니다.
    public static final String SESSION_ID_CLAIM_NAME = "sid";
    // 실제 API 접근에 사용할 수 있는 토큰 타입입니다.
    public static final String ACCESS_TOKEN_TYPE = "access";
    // 토큰 재발급에만 사용할 토큰 타입입니다.
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    // refreshToken을 담는 HttpOnly 쿠키의 이름입니다. 발급(로그인·재발급)과 삭제(로그아웃)에서 함께 씁니다.
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    // 쿠키를 인증 API로만 좁혀, 다른 모든 요청에 자동으로 딸려가지 않게 합니다(CSRF 노출 범위 최소화).
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";
    // 인증 없이 접근 가능한 최소 공개 경로입니다. 여기에 없는 모든 요청은 access token이 필요합니다.
    // 로그아웃은 "누가·어느 세션인지"를 알아야 하므로 공개 경로가 아닙니다.
    public static final String[] AUTH_WHITELIST = {
            // 토큰을 아직 못 받은 상태에서 호출하는 인증 API
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            // API 문서 (테스트 시에는 우측 상단 Authorize 버튼으로 토큰을 넣습니다)
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // 테스트 페이지 (WebConfig에서 정적 매핑) — 테스트 종료 후 함께 제거
            "/test.html",
            // 예외 처리 중 forward 되는 경로. 막아두면 실제 에러 대신 401이 나갑니다.
            "/error"
    };
}
