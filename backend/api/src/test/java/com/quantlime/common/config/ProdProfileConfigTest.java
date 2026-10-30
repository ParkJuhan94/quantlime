package com.quantlime.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 운영 프로파일(application-prod.yml)에만 고정해 둔 값이 실수로 빠지지 않는지 본다. 이 값들은 코드 기본값이 반대(켜짐)라
 * yml에서 빠져도 앱은 정상 기동하고 운영에서만 조용히 예전 동작으로 돌아가므로, 스프링 컨텍스트 없이 yml만 읽어 고정한다.
 */
@Tag("unit")
class ProdProfileConfigTest {

    private static Object prodProperty(String key) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("prod", new ClassPathResource("application-prod.yml"));
        return sources.stream().map(source -> source.getProperty(key)).filter(value -> value != null).findFirst()
            .orElse(null);
    }

    @Test
    @DisplayName("[운영은 유튜브 자막 자체 수집을 끈다 - 운영 IP를 YouTube가 차단해 로컬 동기화로만 채운다]")
    void prod_disablesTranscriptFetch() throws IOException {
        assertThat(prodProperty("videofeed.transcript.fetch-enabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("[운영은 전종목 fan-out인 가격 갱신 DLT의 건별 Slack 알림을 끈다 - 집계 알림만 남긴다]")
    void prod_suppressesPriceRefreshDltSlack() throws IOException {
        assertThat(String.valueOf(prodProperty("kafka.dlt.slack-suppressed-domains")).split("\\s*,\\s*"))
            .contains("videofeed-transcript", "market-price-refresh");
    }
}
