package com.quantlime.price.service;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.cache.MinuteChartCacheStore;
import com.quantlime.price.dto.response.MinuteChartResponse;
import com.quantlime.stock.service.StockMasterService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 종목 분봉(1분봉) 차트 - 저장 없이 토스를 온디맨드로 호출하고 {@link MinuteChartCacheStore}로
 * 짧게 캐싱한다. 호출당 최대 {@value #PAGE_SIZE}개(토스 API 제약, 약 3.3시간 분량)라 더 과거는
 * 응답의 {@code nextBefore} 커서로 이어서 요청한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMinuteChartService {

    static final int PAGE_SIZE = 200;

    private final StockMasterService stockMasterService;
    private final TossApiClient tossApiClient;
    private final MinuteChartCacheStore minuteChartCacheStore;

    /**
     * @param before null이면 가장 최근부터. 값이 있으면 UTC {@code Z} 표기 시각 직전부터 과거 방향
     *               (토스 쿼리스트링의 {@code +}가 공백으로 해석되는 문제 때문에 {@code +09:00} 오프셋 금지 -
     *               {@link TossApiClient#get1MinuteCandleBefore} 참고)
     */
    public MinuteChartResponse getMinuteChart(String stockCode, String before) {
        String symbol = stockMasterService.getStockByCode(stockCode).getStockCode();

        return minuteChartCacheStore.find(symbol, before).orElseGet(() -> {
            TossCandleResponse response = tossApiClient.getMinuteCandles(symbol, PAGE_SIZE, before);
            MinuteChartResponse result = toResponse(response);
            minuteChartCacheStore.save(symbol, before, result);
            return result;
        });
    }

    private MinuteChartResponse toResponse(TossCandleResponse response) {
        if (response == null || response.result() == null || response.result().candles() == null) {
            return new MinuteChartResponse(List.of(), null);
        }
        List<MinuteChartResponse.Candle> candles = response.result().candles().stream()
            .map(candle -> new MinuteChartResponse.Candle(
                OffsetDateTime.parse(candle.timestamp()).toEpochSecond(),
                Double.parseDouble(candle.openPrice()),
                Double.parseDouble(candle.highPrice()),
                Double.parseDouble(candle.lowPrice()),
                Double.parseDouble(candle.closePrice()),
                Double.parseDouble(candle.volume())))
            .sorted(Comparator.comparingLong(MinuteChartResponse.Candle::time))
            .toList();
        return new MinuteChartResponse(candles, toUtcCursor(response.result().nextBefore()));
    }

    // 토스가 돌려주는 nextBefore는 +09:00 같은 오프셋을 가질 수 있어, 그대로 다음 요청의
    // before로 쓰면 +가 공백으로 해석돼 깨진다 - 항상 UTC Z 표기로 정규화해 내려준다.
    private String toUtcCursor(String nextBefore) {
        if (nextBefore == null || nextBefore.isBlank()) {
            return null;
        }
        try {
            return Instant.from(OffsetDateTime.parse(nextBefore)).toString();
        } catch (DateTimeParseException e) {
            log.warn("분봉 nextBefore 파싱 실패, 페이지네이션 종료로 처리: nextBefore={}", nextBefore);
            return null;
        }
    }
}
