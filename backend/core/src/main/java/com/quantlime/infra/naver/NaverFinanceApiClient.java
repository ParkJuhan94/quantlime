package com.quantlime.infra.naver;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.naver.dto.NaverExchangeRateBasicResponse;
import com.quantlime.infra.naver.dto.NaverExchangeRateCandleResponse;
import com.quantlime.infra.naver.dto.NaverIndexBasicResponse;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlime.infra.naver.dto.NaverIndexMinuteCandleResponse;
import com.quantlime.infra.naver.dto.NaverStockFinanceAnnualResponse;
import com.quantlime.infra.naver.dto.NaverStockIntegrationResponse;
import com.quantlime.infra.naver.exception.NaverFinanceApiErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 금융(m.stock.naver.com)의 비공식 모바일 API. 토스증권 Open API가
 * 지수 심볼을 제공하지 않아(§4) 코스피/코스닥 실데이터를 위해서만
 * 제한적으로 연동한다 - 문서화된 계약이 아니므로(종목 로고 연동과 동일한
 * 판단, StockMapper 참고) 사전 검증 없이 호출하고 실패는 그대로 전파해
 * 호출측(MarketIndexCache)이 예시 데이터로 폴백하게 한다.
 */
@Component
@RequiredArgsConstructor
public class NaverFinanceApiClient {

    private final RestClient naverFinanceRestClient;
    private final RestClient naverFinanceChartRestClient;

    public NaverIndexBasicResponse getIndexBasic(String indexCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED,
            () -> naverFinanceRestClient.get()
                .uri("/api/index/{code}/basic", indexCode)
                .retrieve()
                .body(NaverIndexBasicResponse.class));
    }

    public List<NaverIndexCandleResponse> getIndexPrices(String indexCode, int pageSize) {
        return getIndexPrices(indexCode, pageSize, 1);
    }

    /**
     * page를 늘려가며 호출하면 이전 페이지 바로 이전 거래일부터 이어서
     * 내려온다(실제 호출로 확인 - pageSize=60, page=1..7이면 2024-10월까지
     * 끊김 없이 이어짐). 백테스트 벤치마크 이력 백필(BenchmarkIndexBackfillService)이
     * pageSize(최대 60) 상한을 페이지네이션으로 우회하기 위해 사용한다.
     */
    public List<NaverIndexCandleResponse> getIndexPrices(String indexCode, int pageSize, int page) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_CHART_INQUIRY_FAILED,
            () -> {
                NaverIndexCandleResponse[] candles = naverFinanceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/api/index/{code}/price")
                        .queryParam("pageSize", pageSize)
                        .queryParam("page", page)
                        .build(indexCode))
                    .retrieve()
                    .body(NaverIndexCandleResponse[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }

    // 당일(휴장이면 최근 거래일) 1분봉 전체를 한 번에 돌려준다 - count 등
    // 페이지네이션 파라미터가 없다(실제 호출로 확인, 09:00~현재/15:30까지
    // 전부 옴). 홈 화면 지수 카드의 당일 라인차트용.
    public List<NaverIndexMinuteCandleResponse> getIndexMinuteCandles(String indexCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_MINUTE_CHART_INQUIRY_FAILED,
            () -> {
                NaverIndexMinuteCandleResponse[] candles = naverFinanceChartRestClient.get()
                    .uri("/chart/domestic/index/{code}/minute", indexCode)
                    .retrieve()
                    .body(NaverIndexMinuteCandleResponse[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }

    // 나스닥/S&P500/SOX 같은 해외지수는 m.stock.naver.com이 아니라
    // api.stock.naver.com(분봉 차트와 같은 호스트)에서, 코드도 국내
    // 지수처럼 "KOSPI" 같은 이름이 아니라 로이터 코드(".IXIC", ".INX",
    // ".SOX")로 조회한다(실제 호출로 확인 - m.stock.naver.com에 같은
    // 코드로 조회하면 400). 응답 필드 구성은 국내 지수 basic/price와
    // 동일해 기존 DTO(NaverIndexBasicResponse/NaverIndexCandleResponse)를
    // 그대로 재사용한다.
    public NaverIndexBasicResponse getWorldIndexBasic(String reutersCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED,
            () -> naverFinanceChartRestClient.get()
                .uri("/index/{code}/basic", reutersCode)
                .retrieve()
                .body(NaverIndexBasicResponse.class));
    }

    public List<NaverIndexCandleResponse> getWorldIndexPrices(String reutersCode, int pageSize) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_CHART_INQUIRY_FAILED,
            () -> {
                NaverIndexCandleResponse[] candles = naverFinanceChartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/index/{code}/price")
                        .queryParam("pageSize", pageSize)
                        .queryParam("page", 1)
                        .build(reutersCode))
                    .retrieve()
                    .body(NaverIndexCandleResponse[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }

    // SOXX 같은 해외 ETF는 지수가 아니라 종목이라 /stock/... 경로로
    // 조회한다(실제 호출로 확인) - 응답 필드 구성은 지수와 동일해 같은
    // DTO를 재사용한다.
    public NaverIndexBasicResponse getWorldStockBasic(String reutersCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED,
            () -> naverFinanceChartRestClient.get()
                .uri("/stock/{code}/basic", reutersCode)
                .retrieve()
                .body(NaverIndexBasicResponse.class));
    }

    public List<NaverIndexCandleResponse> getWorldStockPrices(String reutersCode, int pageSize) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_CHART_INQUIRY_FAILED,
            () -> {
                NaverIndexCandleResponse[] candles = naverFinanceChartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/stock/{code}/price")
                        .queryParam("pageSize", pageSize)
                        .queryParam("page", 1)
                        .build(reutersCode))
                    .retrieve()
                    .body(NaverIndexCandleResponse[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }

    // 환율은 지수/분봉과 또 다른 경로(api.stock.naver.com/marketindex/...)로
    // 제공된다(실제 호출로 확인). 일별 종가만 필요해 최소 필드 DTO로 받는다.
    public List<NaverExchangeRateCandleResponse> getExchangeRatePrices(String pair, int pageSize) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_CHART_INQUIRY_FAILED,
            () -> {
                NaverExchangeRateCandleResponse[] candles = naverFinanceChartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/marketindex/exchange/{pair}/prices")
                        .queryParam("pageSize", pageSize)
                        .queryParam("page", 1)
                        .build(pair))
                    .retrieve()
                    .body(NaverExchangeRateCandleResponse[].class);
                return candles == null ? null : Arrays.asList(candles);
            });
    }

    // 일별 종가 이력(prices)과 별개로, 당일 등락률까지 포함한 "현재" 값은
    // 접미사 없는 이 경로에서 준다(실제 호출로 확인) - 토스 환율 API는
    // rate/changeType만 주고 등락률(%)이 없어 보완용으로 쓴다.
    public NaverExchangeRateBasicResponse getExchangeRateBasic(String pair) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.INDEX_INQUIRY_FAILED,
            () -> naverFinanceChartRestClient.get()
                .uri("/marketindex/exchange/{pair}", pair)
                .retrieve()
                .body(NaverExchangeRateBasicResponse.class));
    }

    // 시총/PER/PBR/추정PER(포워드 PER) 등은 종목 통합 정보 엔드포인트
    // 하나에 totalInfos 배열로 섞여 내려온다(실제 호출로 확인).
    public NaverStockIntegrationResponse getStockIntegration(String stockCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.STOCK_FUNDAMENTALS_INQUIRY_FAILED,
            () -> naverFinanceRestClient.get()
                .uri("/api/stock/{code}/integration", stockCode)
                .retrieve()
                .body(NaverStockIntegrationResponse.class));
    }

    // 부채비율·매출액(PSR 계산용)은 통합 정보에 없고 연간 재무제표
    // 엔드포인트에서 연도별 표로 내려온다(실제 호출로 확인).
    public NaverStockFinanceAnnualResponse getStockFinanceAnnual(String stockCode) {
        return ExternalApiInvoker.call(
            NaverFinanceApiErrorCode.STOCK_FUNDAMENTALS_INQUIRY_FAILED,
            () -> naverFinanceRestClient.get()
                .uri("/api/stock/{code}/finance/annual", stockCode)
                .retrieve()
                .body(NaverStockFinanceAnnualResponse.class));
    }
}
