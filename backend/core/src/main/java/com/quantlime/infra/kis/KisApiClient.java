package com.quantlime.infra.kis;

import com.quantlime.common.exception.ErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.kis.dto.KisOverseasDailyPriceResponse;
import com.quantlime.infra.kis.dto.KisOverseasPriceResponse;
import com.quantlime.infra.kis.exception.KisApiErrorCode;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 해외주식 현재가/기간별 일별시세 조회. 토스는 국내 전용이라(CLAUDE.md §4)
 * 해외 종목은 KIS(한국투자증권) Open API로 연동한다.
 *
 * <p>tr_id/파라미터/헤더 구성은 2026-07-16 실제 앱키로 라이브 호출
 * (NAS/AAPL 현재가·일별시세 둘 다 rt_cd=0 정상 응답)해 검증 완료.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisApiClient {

    private static final String TR_ID_OVERSEAS_PRICE = "HHDFS00000300";
    private static final String TR_ID_OVERSEAS_DAILY_PRICE = "HHDFS76240000";
    private static final String CUST_TYPE_PERSONAL = "P";
    // KIS는 HTTP 200이어도 rt_cd(자체 성공/실패 코드)가 "0"이 아니면 API
    // 레벨 실패다(예: 잘못된 심볼). ExternalApiInvoker는 null 응답만 체크하고
    // rt_cd는 모르므로, 이를 검증하지 않으면 실패 응답의 빈 필드를
    // Double.parseDouble 등으로 그대로 파싱하려다 의미 불명확한
    // NumberFormatException이 새어나간다.
    private static final String RT_CD_SUCCESS = "0";
    // 수정주가 반영(분할·배당락 보정). 백테스트·지표 계산 정합성을 위해 고정.
    private static final String MODP_ADJUSTED = "1";
    // 일봉 고정(주봉 1, 월봉 2는 사용하지 않음).
    private static final String GUBN_DAILY = "0";

    private final RestClient kisRestClient;
    private final KisApiProperties properties;
    private final KisTokenManager tokenManager;

    /**
     * 해외주식 현재가. exchangeCode 예: NAS(나스닥), NYS(뉴욕), AMS(아멕스).
     */
    public KisOverseasPriceResponse getOverseasPrice(String exchangeCode, String symbol) {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            KisApiErrorCode.OVERSEAS_PRICE_INQUIRY_FAILED,
            () -> {
                KisOverseasPriceResponse response = kisRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", exchangeCode)
                        .queryParam("SYMB", symbol)
                        .build())
                    .headers(headers -> applyHeaders(headers, token, TR_ID_OVERSEAS_PRICE))
                    .retrieve()
                    .body(KisOverseasPriceResponse.class);
                return validateRtCd(response, response == null ? null : response.rtCd(),
                    KisApiErrorCode.OVERSEAS_PRICE_INQUIRY_FAILED);
            },
            HttpClientErrorException.TooManyRequests.class,
            KisApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * 해외주식 기간별(일봉) 시세. baseDate는 조회 기준일(YYYYMMDD, null이면
     * 최근 기준). 수정주가 반영(MODP=1) 고정 - Toss 국내 캔들과 동일하게
     * 백테스트 정합성을 위해 항상 수정주가로 받는다.
     */
    public KisOverseasDailyPriceResponse getOverseasDailyPrice(
        String exchangeCode, String symbol, String baseDate) {
        return withTokenRetry(token -> ExternalApiInvoker.call(
            KisApiErrorCode.OVERSEAS_DAILY_PRICE_INQUIRY_FAILED,
            () -> {
                KisOverseasDailyPriceResponse response = kisRestClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                            .path("/uapi/overseas-price/v1/quotations/dailyprice")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", exchangeCode)
                            .queryParam("SYMB", symbol)
                            .queryParam("GUBN", GUBN_DAILY)
                            .queryParam("MODP", MODP_ADJUSTED);
                        builder.queryParam("BYMD", baseDate == null ? "" : baseDate);
                        return builder.build();
                    })
                    .headers(headers -> applyHeaders(headers, token, TR_ID_OVERSEAS_DAILY_PRICE))
                    .retrieve()
                    .body(KisOverseasDailyPriceResponse.class);
                return validateRtCd(response, response == null ? null : response.rtCd(),
                    KisApiErrorCode.OVERSEAS_DAILY_PRICE_INQUIRY_FAILED);
            },
            HttpClientErrorException.TooManyRequests.class,
            KisApiErrorCode.RATE_LIMIT_EXCEEDED));
    }

    /**
     * HTTP 200이어도 KIS 자체 응답 코드(rt_cd)가 "0"이 아니면 실패로 취급한다
     * (예: 잘못된 심볼) - 그대로 두면 빈 필드를 파싱하려다 의미 불명확한
     * 예외가 새어나간다.
     */
    private <T> T validateRtCd(T response, String rtCd, ErrorCode errorCode) {
        if (response == null || !RT_CD_SUCCESS.equals(rtCd)) {
            throw new ExternalApiException(errorCode);
        }
        return response;
    }

    private void applyHeaders(HttpHeaders headers, String token, String trId) {
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", trId);
        headers.set("custtype", CUST_TYPE_PERSONAL);
    }

    /**
     * 캐시된 토큰이 만료 이전에 거부(401)당하는 경우를 대비해, 캐시를
     * 지우고 새로 발급받은 토큰으로 1회만 재시도한다(TossApiClient의
     * withTokenRetry와 동일 패턴).
     */
    private <T> T withTokenRetry(Function<String, T> apiCall) {
        String token = tokenManager.getAccessToken();
        try {
            return apiCall.apply(token);
        } catch (ExternalApiException e) {
            if (!(e.getCause() instanceof HttpClientErrorException.Unauthorized)) {
                throw e;
            }
            log.warn("KIS API 토큰이 무효화된 것으로 감지, 재발급 후 1회 재시도");
            tokenManager.invalidateToken();
            return apiCall.apply(tokenManager.getAccessToken());
        }
    }
}
