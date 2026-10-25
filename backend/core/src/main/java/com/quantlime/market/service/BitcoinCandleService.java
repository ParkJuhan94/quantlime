package com.quantlime.market.service;

import com.quantlime.market.implement.BitcoinCandleCollector;
import com.quantlime.price.cache.MinuteChartCacheStore;
import com.quantlime.price.dto.response.MinuteChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 비트코인 상세 화면의 캔들 차트 - 업비트 호출({@link BitcoinCandleCollector})을 {@link MinuteChartCacheStore}로
 * 캐싱한다. 이 저장소는 키가 {@code chart:1m:<심볼>:<커서>}인 종목 분봉용이지만 심볼 문자열만 다르면 그대로
 * 쓸 수 있어, 별도 저장소를 만드는 대신 {@code BTC:<단위>}를 심볼로 넣어 재사용한다(주식 코드와 겹치지 않음).
 * 최신 페이지 TTL은 15초, {@code before}를 지정한 과거 페이지는 30분이다.
 */
@Service
@RequiredArgsConstructor
public class BitcoinCandleService {

    private final BitcoinCandleCollector bitcoinCandleCollector;
    private final MinuteChartCacheStore minuteChartCacheStore;

    public MinuteChartResponse getCandles(String interval, @Nullable String before) {
        String cacheSymbol = "BTC:" + interval;
        return minuteChartCacheStore.find(cacheSymbol, before).orElseGet(() -> {
            MinuteChartResponse result = bitcoinCandleCollector.fetch(interval, before);
            minuteChartCacheStore.save(cacheSymbol, before, result);
            return result;
        });
    }
}
