package com.quantlab.infra.naver;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "naver-finance")
public class NaverFinanceApiProperties {

    private final String baseUrl;
    // 분봉 차트는 m.stock.naver.com이 아니라 별도 호스트(api.stock.naver.com)에서
    // 제공된다 - basic/일봉 조회 API와 호스트 자체가 다르다(실제 호출로 확인).
    private final String chartBaseUrl;
}
