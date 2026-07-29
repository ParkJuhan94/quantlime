package com.quantlime.price.cache;

import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 해외 종목별 전일 종가(Double, USD)를 캘린더 날짜 기준으로 캐싱한다.
 * 국내 {@link PreviousCloseCache}와 동일한 목적·구조 - 해외 관심종목
 * 실시간가/랭킹의 자체 등락률 계산(전일 종가 대비)용. 가격이 소수라
 * 값 타입만 다르게 별도 클래스로 둔다(§ {@code OverseasDailyPrice} 분리와
 * 동일한 이유).
 */
@Component
@RequiredArgsConstructor
public class OverseasPreviousCloseCache {

    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    private volatile Map<String, Double> closeByStockCode = Map.of();
    private volatile LocalDate cachedDate = LocalDate.MIN;

    public Map<String, Double> get(List<String> stockCodes) {
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
        closeByStockCode = overseasDailyPriceRepository.findLatestBeforeDate(stockCodes, today).stream()
            .collect(Collectors.toMap(OverseasDailyPrice::getStockCode, OverseasDailyPrice::getClosePrice));
        cachedDate = today;
    }
}
