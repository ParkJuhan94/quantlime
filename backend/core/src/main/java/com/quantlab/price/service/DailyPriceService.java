package com.quantlab.price.service;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossCandleResponse;
import com.quantlab.infra.toss.dto.TossPriceMapper;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.repository.DailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceService {

    private final DailyPriceRepository dailyPriceRepository;
    private final TossApiClient tossApiClient;

    @Transactional
    public void collectDailyPrice(String stockCode, LocalDate targetDate) {
        if (dailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, targetDate)) {
            log.debug("이미 수집된 데이터: stockCode={}, date={}", stockCode, targetDate);
            return;
        }

        TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, 1, null);

        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("시세 데이터 없음: stockCode={}, date={}", stockCode, targetDate);
            return;
        }

        List<DailyPrice> prices = candles.stream()
            .filter(c -> TossPriceMapper.toLocalDate(c.timestamp()).equals(targetDate))
            .map(c -> TossPriceMapper.toDailyPrice(stockCode, c))
            .toList();

        if (prices.isEmpty()) {
            log.warn("대상 날짜 시세 없음: stockCode={}, date={}", stockCode, targetDate);
            return;
        }

        dailyPriceRepository.saveAll(prices);
        log.info("일별 시세 수집 완료: stockCode={}, count={}", stockCode, prices.size());
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getDailyPrices(String stockCode, LocalDate start, LocalDate end) {
        return dailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end);
    }
}
