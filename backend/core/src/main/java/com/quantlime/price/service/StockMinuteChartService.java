package com.quantlime.price.service;

import com.quantlime.price.cache.MinuteChartCacheStore;
import com.quantlime.price.dto.response.MinuteChartResponse;
import com.quantlime.price.implement.MinuteChartCollector;
import com.quantlime.stock.service.StockMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 종목 분봉(1분봉) 차트 - 저장 없이 토스를 온디맨드로 호출({@link MinuteChartCollector})하고
 * {@link MinuteChartCacheStore}로 짧게 캐싱한다. 더 과거는 응답의 {@code nextBefore} 커서로
 * 이어서 요청한다.
 */
@Service
@RequiredArgsConstructor
public class StockMinuteChartService {

    private final StockMasterService stockMasterService;
    private final MinuteChartCollector minuteChartCollector;
    private final MinuteChartCacheStore minuteChartCacheStore;

    /**
     * @param before null이면 가장 최근부터. 값이 있으면 UTC {@code Z} 표기 시각 직전부터 과거 방향
     *               ({@code +09:00} 오프셋 금지 - {@link MinuteChartCollector#fetch} 참고)
     */
    public MinuteChartResponse getMinuteChart(String stockCode, String before) {
        String symbol = stockMasterService.getStockByCode(stockCode).getStockCode();

        return minuteChartCacheStore.find(symbol, before).orElseGet(() -> {
            MinuteChartResponse result = minuteChartCollector.fetch(symbol, before);
            minuteChartCacheStore.save(symbol, before, result);
            return result;
        });
    }
}
