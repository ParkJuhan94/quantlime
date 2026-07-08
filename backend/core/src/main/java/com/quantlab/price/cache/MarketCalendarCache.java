package com.quantlab.price.cache;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 국내 장 운영 여부(공휴일 포함)를 캘린더 날짜 기준으로 캐싱한다. 하루
 * 1회만 Toss 장 운영 캘린더 API를 호출하고, 그 전까지는 캐시된 결과를
 * 반환한다 - PriceBroadcastScheduler가 3초 틱마다 이 API를 다시 부르지
 * 않도록 하기 위함.
 *
 * <p>정규장(regularMarket)만이 아니라 NXT 프리마켓(preMarket, 08:00~09:00)/
 * 애프터마켓(afterMarket, 15:30~20:00)도 "장중"으로 취급한다 - 정규장만
 * 보면 프리마켓 시간대의 실시간 시세가 브로드캐스트되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarCache {

    private final TossApiClient tossApiClient;

    private volatile LocalDate cachedDate = LocalDate.MIN;
    private volatile List<TossMarketCalendarResponse.MarketSession> tradingSessions = List.of();

    public boolean isMarketOpenNow() {
        LocalDate today = LocalDate.now();
        if (!cachedDate.equals(today)) {
            refresh(today);
        }
        if (tradingSessions.isEmpty()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        return tradingSessions.stream().anyMatch(session -> isWithin(now, session));
    }

    private boolean isWithin(OffsetDateTime now, TossMarketCalendarResponse.MarketSession session) {
        OffsetDateTime start = OffsetDateTime.parse(session.startTime());
        OffsetDateTime end = OffsetDateTime.parse(session.endTime());
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private synchronized void refresh(LocalDate today) {
        if (cachedDate.equals(today)) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        TossMarketCalendarResponse response = tossApiClient.getMarketCalendar();
        TossMarketCalendarResponse.MarketDay todayInfo = response.result().today();
        TossMarketCalendarResponse.MarketSessions sessions = todayInfo.integrated();
        this.tradingSessions = sessions == null
            ? List.of()
            : Stream.of(sessions.preMarket(), sessions.regularMarket(), sessions.afterMarket())
                .filter(Objects::nonNull)
                .toList();
        this.cachedDate = today;
        log.info("장 운영 캘린더 갱신: date={}, 개장여부={}, 세션수={}",
            today, !this.tradingSessions.isEmpty(), this.tradingSessions.size());
    }
}
