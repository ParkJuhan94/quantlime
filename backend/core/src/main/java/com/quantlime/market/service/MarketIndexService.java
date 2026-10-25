package com.quantlime.market.service;

import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.DomesticIndexChartCache;
import com.quantlime.market.cache.DomesticIndexMinuteChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.OverseasIndexChartCache;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.domain.OverseasIndexCode;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import com.quantlime.market.dto.response.MarketIndexResponse;
import com.quantlime.market.implement.BenchmarkIndexReader;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private static final Set<String> DOMESTIC_CODES = Set.of("KOSPI", "KOSDAQ");
    // benchmark_index에 영속 저장되는 해외지수(BenchmarkIndexBackfillService의 OVERSEAS_INDEX_CODES와 같은 집합).
    // SOXX는 ETF라 벤치마크가 아니어서 저장되지 않는다.
    private static final Set<OverseasIndexCode> PERSISTED_OVERSEAS_CODES =
        Set.of(OverseasIndexCode.NASDAQ, OverseasIndexCode.SP500);
    // 지수 상세 차트가 보여줄 일봉 기간 - 월봉 집계와 이동평균에 쓸 이력이 충분하도록 benchmark_index가
    // 쌓아둔 만큼(백필 목표 약 2년, BenchmarkIndexBackfillService 참고) 내려준다.
    private static final int CHART_LOOKBACK_DAYS = 730;

    private final MarketIndexCache marketIndexCache;
    private final DomesticIndexChartCache domesticIndexChartCache;
    private final DomesticIndexMinuteChartCache domesticIndexMinuteChartCache;
    private final OverseasIndexChartCache overseasIndexChartCache;
    private final BitcoinChartCache bitcoinChartCache;
    private final ExchangeRateChartCache exchangeRateChartCache;
    private final BenchmarkIndexReader benchmarkIndexReader;
    private final Clock clock;

    public MarketIndexResponse getIndices() {
        return marketIndexCache.get();
    }

    /**
     * 국내(코스피/코스닥)는 영속 저장된 {@code benchmark_index}(종목 상세
     * 페이지의 DomesticDailyPrice와 동일한 성격 - 매일 최신화됨, 2026-07-30 이관)를
     * 직접 조회한다. 예전엔 {@link DomesticIndexChartCache}(Toss 실시간 조회, 60초
     * TTL, 영속 저장 안 함)를 썼는데, 종목처럼 영속 이력에서 조회되길
     * 원하는 사용자 요청으로 교체 - 추가 API 호출 없이 이미 매일 갱신되는
     * 데이터를 재사용한다. 해외지수는 이 영속 이력이 없어(비공식 네이버
     * 스크래핑 60초 TTL만 있음) 기존 방식을 유지한다.
     */
    public List<IndexChartResponse> getIndexChart(String code) {
        if (DOMESTIC_CODES.contains(code)) {
            return benchmarkIndexReader
                .findBetween(code, LocalDate.now(clock).minusDays(CHART_LOOKBACK_DAYS), LocalDate.now(clock))
                .stream()
                .map(this::toChartResponse)
                .toList();
        }
        OverseasIndexCode overseasIndexCode = OverseasIndexCode.from(code);
        if (!PERSISTED_OVERSEAS_CODES.contains(overseasIndexCode)) {
            return overseasIndexChartCache.get(overseasIndexCode.getReutersCode(), overseasIndexCode.isEtf());
        }
        return mergePersistedWithRecent(code, overseasIndexCode);
    }

    // 나스닥·S&P500은 benchmark_index의 긴 이력(약 2년)에 네이버 최근 60일을 덮어 합친다 - 이력은 일 1회 갱신이라
    // 오늘 봉이 없거나 낡을 수 있어 같은 날짜는 네이버 값을 우선한다. 네이버가 실패해도 이력만으로 차트는 보여준다.
    private List<IndexChartResponse> mergePersistedWithRecent(String code, OverseasIndexCode overseasIndexCode) {
        Map<LocalDate, IndexChartResponse> byDate = new TreeMap<>();
        LocalDate today = LocalDate.now(clock);
        benchmarkIndexReader.findBetween(code, today.minusDays(CHART_LOOKBACK_DAYS), today)
            .forEach(benchmarkIndex -> byDate.put(benchmarkIndex.getTradeDate(), toChartResponse(benchmarkIndex)));
        try {
            overseasIndexChartCache.get(overseasIndexCode.getReutersCode(), overseasIndexCode.isEtf())
                .forEach(candle -> byDate.put(candle.tradeDate(), candle));
        } catch (RuntimeException e) {
            if (byDate.isEmpty()) {
                throw e;
            }
            log.warn("해외지수 최근 시세 조회 실패, 저장된 이력만으로 응답: code={}, error={}", code, e.getMessage());
        }
        return List.copyOf(byDate.values());
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
        List<IndexMinuteChartResponse> minuteChart = domesticIndexMinuteChartCache.get(code);
        if (!minuteChart.isEmpty()) {
            return minuteChart;
        }
        return domesticIndexChartCache.get(code).stream()
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
