package com.quantlab.market.service;

import com.quantlab.market.cache.BitcoinChartCache;
import com.quantlab.market.cache.ExchangeRateChartCache;
import com.quantlab.market.cache.IndexChartCache;
import com.quantlab.market.cache.IndexMinuteChartCache;
import com.quantlab.market.cache.MarketIndexCache;
import com.quantlab.market.cache.WorldIndexChartCache;
import com.quantlab.market.domain.WorldIndexCode;
import com.quantlab.market.dto.response.IndexChartResponse;
import com.quantlab.market.dto.response.IndexMinuteChartResponse;
import com.quantlab.market.dto.response.MarketIndexResponse;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketIndexService {

    private static final Set<String> DOMESTIC_CODES = Set.of("KOSPI", "KOSDAQ");

    private final MarketIndexCache marketIndexCache;
    private final IndexChartCache indexChartCache;
    private final IndexMinuteChartCache indexMinuteChartCache;
    private final WorldIndexChartCache worldIndexChartCache;
    private final BitcoinChartCache bitcoinChartCache;
    private final ExchangeRateChartCache exchangeRateChartCache;

    public MarketIndexResponse getIndices() {
        return marketIndexCache.get();
    }

    // 국내(KOSPI/KOSDAQ)는 당일 분봉이 있어 그대로 쓰지만, 해외지수는
    // 분봉 조회가 불가능해(실제 호출로 확인) 일봉 이력으로 대신한다 -
    // 코드값으로 어느 캐시를 쓸지만 분기하고 나머지 로직은 각 캐시에 위임.
    public List<IndexChartResponse> getIndexChart(String code) {
        if (DOMESTIC_CODES.contains(code)) {
            return indexChartCache.get(code);
        }
        WorldIndexCode worldIndexCode = WorldIndexCode.from(code);
        return worldIndexChartCache.get(worldIndexCode.getReutersCode(), worldIndexCode.isEtf());
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
