package HK.PrettyWorks_BE.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// 비밀번호 해싱/매칭에 사용하는 PasswordEncoder를 등록하는 설정입니다.
@Configuration
public class PasswordConfig {
    // 회원가입 시 비밀번호 해싱, 로그인 시 매칭에 사용하는 인코더입니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
