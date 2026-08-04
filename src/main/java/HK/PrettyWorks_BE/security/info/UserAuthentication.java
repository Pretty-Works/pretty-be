package HK.PrettyWorks_BE.security.info;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

// Spring SecurityContext에 저장할 "인증된 사용자" 표현 객체입니다.
// principal은 userId(Long) 그대로 둡니다 — 컨트롤러들이 @AuthenticationPrincipal Long userId로 받고 있어서,
// 여기에 다른 타입을 담으면 전 API에서 값이 null이 됩니다. 세션 식별자는 별도 필드로만 보관합니다.
@Getter
public class UserAuthentication extends UsernamePasswordAuthenticationToken {

    // 어느 로그인(기기)에서 온 요청인지. 로그아웃처럼 세션을 특정해야 하는 곳에서만 사용합니다.
    private final String sessionId;

    public UserAuthentication(
            Object principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities,
            String sessionId
    ) {
        super(principal, credentials, authorities);
        this.sessionId = sessionId;
    }

    // 권한/비밀번호 없이 userId를 principal로, 세션 식별자를 부가 정보로 보관하는 인증 객체를 만듭니다.
    public static UserAuthentication createUserAuthentication(Long userId, String sessionId) {
        return new UserAuthentication(userId, null, null, sessionId);
    }
}
