package com.quantlime.market.service;

import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.IndexChartCache;
import com.quantlime.market.cache.IndexMinuteChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.WorldIndexChartCache;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.domain.WorldIndexCode;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import com.quantlime.market.dto.response.MarketIndexResponse;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private static final Set<String> DOMESTIC_CODES = Set.of("KOSPI", "KOSDAQ");
    // 종목 상세 차트(PriceController @Max(365))와 비슷한 기간을 보여준다 -
    // benchmark_index가 실제로 그만큼 쌓여 있다(백테스트 벤치마크 백필,
    // BenchmarkIndexBackfillService 참고).
    private static final int CHART_LOOKBACK_DAYS = 365;

    private final MarketIndexCache marketIndexCache;
    private final IndexChartCache indexChartCache;
    private final IndexMinuteChartCache indexMinuteChartCache;
    private final WorldIndexChartCache worldIndexChartCache;
    private final BitcoinChartCache bitcoinChartCache;
    private final ExchangeRateChartCache exchangeRateChartCache;
    private final BenchmarkIndexRepository benchmarkIndexRepository;

    public MarketIndexResponse getIndices() {
        return marketIndexCache.get();
    }

    /**
     * 국내(코스피/코스닥)는 영속 저장된 {@code benchmark_index}(종목 상세
     * 페이지의 DailyPrice와 동일한 성격 - 매일 최신화됨, 2026-07-30 이관)를
     * 직접 조회한다. 예전엔 {@link IndexChartCache}(Toss 실시간 조회, 60초
     * TTL, 영속 저장 안 함)를 썼는데, 종목처럼 영속 이력에서 조회되길
     * 원하는 사용자 요청으로 교체 - 추가 API 호출 없이 이미 매일 갱신되는
     * 데이터를 재사용한다. 해외지수는 이 영속 이력이 없어(비공식 네이버
     * 스크래핑 60초 TTL만 있음) 기존 방식을 유지한다.
     */
    public List<IndexChartResponse> getIndexChart(String code) {
        if (DOMESTIC_CODES.contains(code)) {
            return benchmarkIndexRepository
                .findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
                    code, LocalDate.now().minusDays(CHART_LOOKBACK_DAYS), LocalDate.now())
                .stream()
                .map(this::toChartResponse)
                .toList();
        }
        WorldIndexCode worldIndexCode = WorldIndexCode.from(code);
        return worldIndexChartCache.get(worldIndexCode.getReutersCode(), worldIndexCode.isEtf());
    }

    private IndexChartResponse toChartResponse(BenchmarkIndex benchmarkIndex) {
        return new IndexChartResponse(
            benchmarkIndex.getTradeDate(),
            benchmarkIndex.getOpenPrice(),
            benchmarkIndex.getHighPrice(),
            benchmarkIndex.getLowPrice(),
            benchmarkIndex.getClosePrice());
    }

    // 네이버 분봉 API는 "당일" 데이터만 주고 과거 조회 파라미터가 없다
    // (실제 호출로 확인) - 장이 아직 열리지 않은 시간대(예: 오전 09시
    // 이전)나 휴장일엔 빈 배열이 온다. 이때는 최근 거래일 일봉을 분봉과
    // 같은 모양(time/price)으로 변환해 대신 보여준다 - "장마감이어도
    // 최근 거래일 차트는 항상 보이게" 하기 위함.
    public List<IndexMinuteChartResponse> getIndexMinuteChart(String code) {
        List<IndexMinuteChartResponse> minuteChart = indexMinuteChartCache.get(code);
        if (!minuteChart.isEmpty()) {
            return minuteChart;
        }
        return indexChartCache.get(code).stream()
            .map(candle -> new IndexMinuteChartResponse(candle.tradeDate().atStartOfDay(), candle.close()))
            .toList();
    }

    public List<IndexMinuteChartResponse> getBitcoinChart() {
        return bitcoinChartCache.get();
    }

    public List<IndexChartResponse> getExchangeRateChart() {
        return exchangeRateChartCache.get();
    }
}
