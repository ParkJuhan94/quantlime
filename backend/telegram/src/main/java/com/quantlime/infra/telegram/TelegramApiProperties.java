package com.quantlime.infra.telegram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "telegram")
public class TelegramApiProperties {

    private final String baseUrl;
    private final String userAgent;
    // 페이지 요청 사이 정중한 대기 시간(ms) - 비공식 공개 페이지를 스크래핑
    // 하는 것이라 별도 rate limit 스펙이 없다. 과도한 요청으로 차단당하지
    // 않도록 최소한의 딜레이를 둔다(docs/ROADMAP.md "Phase 8 P7" 참고).
    private final long requestDelayMs;
}
