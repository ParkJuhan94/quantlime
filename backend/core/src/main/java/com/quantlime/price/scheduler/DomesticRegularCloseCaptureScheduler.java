package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticRegularClosePriceRepository;
import com.quantlime.stock.domain.Stock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 *
 * <p><b>기동 시 캐치업({@link #captureIfWithinStartupSafeWindow}, {@code
 * StartupCatchUpRunner}에서 호출)은 반드시 15:30~{@value
 * #STARTUP_CATCHUP_GRACE_MINUTES}분 이내의 좁은 시간대에서만 캡처를
 * 시도한다</b> - 로컬 개발처럼 서버가 항상 떠있지 않은 환경에서 배포/재기동이
 * 15:30을 지나 이뤄지면 그날 정규 cron이 아예 발동하지 못하는데, 이때 시간대
 * 제한 없이 그냥 "지금 Redis 값"을 캡처하면 위험하다 - NXT 애프터마켓
 * (15:30~20:00) 동안 스윕이 계속 값을 덮어쓰고 있어, 창을 넘겨서 캡처하면
 * 이미 드리프트된 애프터마켓 가격을 "정규장 종가"로 확정 저장하게 되고,
 * 그러면 {@code DomesticPreviousCloseResolver}가 폴백 없이 그 틀린 값을 그대로
 * 쓰게 돼 이 클래스가 애초에 막으려던 문제를 스스로 재현한다(2026-08-17
 * 실사용 중 발견). 창을 넘긴 날짜/종목은 캡처를 포기하고 기존 폴백(일봉
 * 종가)에 맡긴다 - 이 프로젝트는 분봉을 저장하지 않아 그 순간이 지나면
 * 사후에 복구할 방법이 없기 때문에, 그 시각을 넘기면 정확도보다 안전(틀린
 * 값을 "확정"으로 잘못 저장하지 않는 것)을 우선한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticRegularCloseCaptureScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_CLOSE_TIME = LocalTime.of(15, 30);
    private static final int STARTUP_CATCHUP_GRACE_MINUTES = 5;

    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    private final DomesticListedStockCache domesticListedStockCache;
    private final PriceCacheStore priceCacheStore;
    private final DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureRegularClose() {
        SafeExecutor.runSafely("정규장 종가 캡처", this::doCapture);
    }

    /**
     * 기동 시 캐치업 전용 진입점 - {@code StartupCatchUpRunner}가 호출한다.
     * 클래스 javadoc 참고: 15:30~15:35(KST) 안전 시간대를 벗어나면 캡처를
     * 시도하지 않고 조용히 넘어간다(그 날은 기존 폴백에 맡김).
     */
    public void captureIfWithinStartupSafeWindow() {
        captureIfWithinStartupSafeWindow(LocalTime.now(KST));
    }

    // now를 파라미터로 받는 오버로드는 테스트에서 시간대 경계를 직접 통제하기 위함
    void captureIfWithinStartupSafeWindow(LocalTime now) {
        LocalTime windowEnd = REGULAR_CLOSE_TIME.plus(Duration.ofMinutes(STARTUP_CATCHUP_GRACE_MINUTES));
        if (now.isBefore(REGULAR_CLOSE_TIME) || now.isAfter(windowEnd)) {
            log.debug("정규장 종가 캡처 기동 캐치업 스킵: 안전 시간대(15:30~15:35) 밖 - now={}", now);
            return;
        }
        doCapture();
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
