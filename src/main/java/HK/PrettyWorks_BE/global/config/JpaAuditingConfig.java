package HK.PrettyWorks_BE.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // @CreatedDate/@LastModifiedDate가 사용할 현재 시각을 KST 기준으로 고정합니다.
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(KST));
    }
}
