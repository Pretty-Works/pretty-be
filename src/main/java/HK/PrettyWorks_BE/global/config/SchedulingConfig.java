package HK.PrettyWorks_BE.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 스케줄러를 활성화합니다. 이 설정이 없으면 @Scheduled 메서드가 아예 실행되지 않습니다.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
