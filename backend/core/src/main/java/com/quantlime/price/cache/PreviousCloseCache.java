package com.quantlime.price.cache;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목별 전일 종가를 캘린더 날짜 기준으로 캐싱한다. 전일 종가는 당일
 * 하루 동안 값이 바뀌지 않으므로 배치 조회는 하루 1회만 수행한다. 단,
 * 캐시된 이후 새로 관심 종목에 등록된 종목처럼 캐시에 없는 코드가
 * 섞여 있으면 그 시점에만 다시 조회한다.
 *
 * <p>당일 행은 DailyPriceQueryRepository.findLatestBeforeDate가 항상
 * 제외하므로, 장중 캐치업 수집으로 당일 OHLCV가 먼저 들어와도 전일
 * 종가가 당일 값으로 잘못 대체되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PreviousCloseCache {

    private final DailyPriceRepository dailyPriceRepository;

    private volatile Map<String, Long> closeByStockCode = Map.of();
    private volatile LocalDate cachedDate = LocalDate.MIN;

    public Map<String, Long> get(List<String> stockCodes) {
        LocalDate today = LocalDate.now();
        if (needsRefresh(stockCodes, today)) {
            refresh(stockCodes, today);
        }
        return closeByStockCode;
    }

    private boolean needsRefresh(List<String> stockCodes, LocalDate today) {
        return !cachedDate.equals(today) || !closeByStockCode.keySet().containsAll(stockCodes);
    }

    private synchronized void refresh(List<String> stockCodes, LocalDate today) {
        if (!needsRefresh(stockCodes, today)) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        closeByStockCode = dailyPriceRepository.findLatestBeforeDate(stockCodes, today).stream()
            .collect(Collectors.toMap(DailyPrice::getStockCode, DailyPrice::getClosePrice));
        cachedDate = today;
    }
}
