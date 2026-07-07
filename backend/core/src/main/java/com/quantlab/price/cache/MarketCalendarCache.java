package com.quantlab.price.cache;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 국내 장 운영 여부(공휴일 포함)를 캘린더 날짜 기준으로 캐싱한다. 하루
 * 1회만 Toss 장 운영 캘린더 API를 호출하고, 그 전까지는 캐시된 결과를
 * 반환한다 - PriceBroadcastScheduler가 3초 틱마다 이 API를 다시 부르지
 * 않도록 하기 위함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarCache {

    private final TossApiClient tossApiClient;

    private volatile LocalDate cachedDate = LocalDate.MIN;
    private volatile TossMarketCalendarResponse.MarketSession regularMarket;

    public boolean isMarketOpenNow() {
        LocalDate today = LocalDate.now();
        if (!cachedDate.equals(today)) {
            refresh(today);
        }
        if (regularMarket == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = OffsetDateTime.parse(regularMarket.startTime());
        OffsetDateTime end = OffsetDateTime.parse(regularMarket.endTime());
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private synchronized void refresh(LocalDate today) {
        if (cachedDate.equals(today)) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        TossMarketCalendarResponse response = tossApiClient.getMarketCalendar();
        TossMarketCalendarResponse.MarketDay todayInfo = response.result().today();
        this.regularMarket = todayInfo.integrated() != null
            ? todayInfo.integrated().regularMarket() : null;
        this.cachedDate = today;
        log.info("장 운영 캘린더 갱신: date={}, 개장여부={}", today, this.regularMarket != null);
    }
}
