package com.quantlime.infra.kis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KIS(한국투자증권)는 해외 전종목 마스터파일(.mst.cod.zip) 다운로드
 * 전용으로만 남아 있다(2026-07-29 - 시세 조회는 Toss로 전량 이관, KIS
 * 앱키/시크릿 인증이 필요한 API 자체가 더 이상 없음).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "kis")
public class KisApiProperties {

    // 해외주식 종목정보 마스터파일(.mst.cod.zip) 다운로드 호스트 - 인증 불필요한
    // 정적 파일 CDN.
    private final String masterFileBaseUrl;
}
