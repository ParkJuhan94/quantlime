package com.quantlime.infra.kis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "kis")
public class KisApiProperties {

    private final String appKey;
    private final String appSecret;
    private final String baseUrl;
    // 해외주식 종목정보 마스터파일(.mst.cod.zip) 다운로드 호스트 - API 서버와
    // 별도 CDN 호스트라 kis.base-url과 분리한다.
    private final String masterFileBaseUrl;
}
