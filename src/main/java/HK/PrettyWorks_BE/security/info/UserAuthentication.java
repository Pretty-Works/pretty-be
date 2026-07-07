package HK.PrettyWorks_BE.security.info;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

// Spring SecurityContext에 저장할 "인증된 사용자" 표현 객체입니다.
public class UserAuthentication extends UsernamePasswordAuthenticationToken {

    public UserAuthentication(
            Object principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(principal, credentials, authorities);
    }

    // 현재는 권한/비밀번호 없이 userId만 principal로 보관하는 가장 단순한 인증 모델입니다.
    public static UserAuthentication createUserAuthentication(Long userId) {
        return new UserAuthentication(userId, null, null);
    }
}
