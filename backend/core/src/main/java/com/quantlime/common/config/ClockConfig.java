package com.quantlime.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 서비스가 LocalDate.now()처럼 JVM 기본 타임존에 기대지 않고 한국 시각 기준으로 "오늘"을 판단하게 하는 공용 시계
// (Error Prone JavaTimeDefaultTimeZone 정리, 2026-10-22). 운영 컨테이너는 TZ=Asia/Seoul이라 기존 동작과 같고,
// 테스트는 Clock.fixed로 시각을 고정할 수 있다
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
