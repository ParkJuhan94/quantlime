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
    public void collectDailyPrice(String stockCode) {
        TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, 1, null);

        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("시세 데이터 없음: stockCode={}", stockCode);
            return;
        }

        TossCandleResponse.TossCandle latest = candles.get(0);
        LocalDate tradeDate = TossPriceMapper.toLocalDate(latest.timestamp());

        if (dailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
            log.debug("이미 수집된 데이터: stockCode={}, date={}", stockCode, tradeDate);
            return;
        }

        dailyPriceRepository.save(TossPriceMapper.toDailyPrice(stockCode, latest));
        log.info("일별 시세 수집 완료: stockCode={}, date={}", stockCode, tradeDate);
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getDailyPrices(String stockCode, LocalDate start, LocalDate end) {
        return dailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end);
    }
}
