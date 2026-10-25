package com.quantlime.market.service;

import com.quantlime.market.implement.IndexMinuteCandleCollector;
import com.quantlime.price.cache.MinuteChartCacheStore;
import com.quantlime.price.dto.response.MinuteChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 코스피/코스닥 상세 화면의 분봉 차트 - 토스 호출({@link IndexMinuteCandleCollector})을
 * {@link MinuteChartCacheStore}로 캐싱한다. 키 충돌을 피하려고 {@code IDX:<지수코드>}를 심볼로 넣어
 * 재사용한다({@link BitcoinCandleService}와 같은 방식). 최신 페이지 TTL 15초, 과거 페이지 30분.
 */
@Service
@RequiredArgsConstructor
public class IndexMinuteCandleService {

    private final IndexMinuteCandleCollector indexMinuteCandleCollector;
    private final MinuteChartCacheStore minuteChartCacheStore;

    public MinuteChartResponse getCandles(String indexCode, @Nullable String before) {
        String cacheSymbol = "IDX:" + indexCode;
        return minuteChartCacheStore.find(cacheSymbol, before).orElseGet(() -> {
            MinuteChartResponse result = indexMinuteCandleCollector.fetch(indexCode, before);
            minuteChartCacheStore.save(cacheSymbol, before, result);
            return result;
        });
    }
}
