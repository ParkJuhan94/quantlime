package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticRegularClosePriceRepository;
import com.quantlime.stock.domain.Stock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정규장 마감(15:30) 직후, 그 시점 {@link PriceCacheStore} 실시간 시세
 * 스냅샷을 그대로 {@link DomesticRegularClosePrice}로 영속화한다.
 *
 * <p>{@code domestic_daily_price.close_price}는 NXT 프리/애프터마켓까지
 * 반영해 20:00까지 계속 갱신되는 값이라({@link DomesticRegularClosePrice}
 * 클래스 javadoc 참고) 전일종가 기준으로 그대로 쓸 수 없다 - {@code
 * DomesticMarketPriceSweepScheduler}가 100ms마다 이미 채워둔 Redis 스냅샷을
 * 재활용하므로 별도 Toss 호출은 없다.
 *
 * <p>고정 cron(15:30)이라 조기폐장 등 정규장 종료 시각이 다른 특수한 날은
 * {@link PriceCacheStore}의 TTL(5분)이 이미 지나 스냅샷을 못 찾을 수 있다 -
 * 그런 종목/날짜는 이번 캡처를 건너뛰고, {@code DomesticPreviousCloseResolver}가
 * {@code domestic_daily_price} 확정 종가로 자동 폴백한다(그날 하루만 기존
 * 동작으로 되돌아갈 뿐 이후 정상화됨).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticRegularCloseCaptureScheduler {

    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    private final DomesticListedStockCache domesticListedStockCache;
    private final PriceCacheStore priceCacheStore;
    private final DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureRegularClose() {
        SafeExecutor.runSafely("정규장 종가 캡처", this::doCapture);
    }

    private void doCapture() {
        if (!domesticMarketCalendarCache.isTradingDayToday()) {
            log.debug("정규장 종가 캡처 스킵: 휴장일");
            return;
        }

        LocalDate today = LocalDate.now();
        List<Stock> stocks = domesticListedStockCache.get();
        int captured = 0;
        for (Stock stock : stocks) {
            if (capture(stock.getStockCode(), today)) {
                captured++;
            }
        }
        log.info("정규장 종가 캡처 완료: 대상={}종목, 캡처={}건", stocks.size(), captured);
    }

    private boolean capture(String stockCode, LocalDate today) {
        Optional<PriceSnapshot> snapshot = priceCacheStore.find(stockCode);
        if (snapshot.isEmpty() || snapshot.get().currentPrice() == null) {
            return false;
        }
        if (domesticRegularClosePriceRepository.existsByStockCodeAndTradeDate(stockCode, today)) {
            return false; // 이미 캡처됨(재기동 등으로 중복 트리거된 경우) - 멱등 처리
        }
        long closePrice = Math.round(snapshot.get().currentPrice());
        try {
            domesticRegularClosePriceRepository.save(DomesticRegularClosePrice.of(stockCode, today, closePrice));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("정규장 종가 캡처 동시 저장 충돌 스킵: stockCode={}", stockCode);
            return false;
        }
    }
}
