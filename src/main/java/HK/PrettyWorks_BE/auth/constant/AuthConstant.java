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
    // 실제 API 접근에 사용할 수 있는 토큰 타입입니다.
    public static final String ACCESS_TOKEN_TYPE = "access";
    // 토큰 재발급에만 사용할 토큰 타입입니다.
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    // 인증 없이 접근 가능한 최소 공개 경로입니다.
    public static final String[] AUTH_WHITELIST = {
            "/**"
    };
}
