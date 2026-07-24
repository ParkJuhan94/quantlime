package com.quantlime.price.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse;
import java.time.Duration;
import java.time.Instant;
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
 * 반환한다 - {@code MarketPriceSweepScheduler}(100ms 틱)와
 * {@code WatchlistPriceRelayScheduler}(3초 틱) 둘 다 매 틱 이 메서드를
 * 호출하지만 이 API를 다시 부르지 않도록 하기 위함.
 *
 * <p>정규장(regularMarket)만이 아니라 NXT 프리마켓(preMarket, 08:00~09:00)/
 * 애프터마켓(afterMarket, 15:30~20:00)도 "장중"으로 취급한다 - 정규장만
 * 보면 프리마켓 시간대의 실시간 시세가 브로드캐스트되지 않는다.
 *
 * <p>{@link #refresh}가 Toss 호출 도중 예외를 던지면 과거엔 {@code cachedDate}가
 * 갱신되지 않아, 그날 이 캐시가 영구히 "미스" 상태로 남고 100ms/3초 틱마다
 * 딜레이 없이 캘린더 API를 재호출해 429를 무한 재생산하는 자기증폭 루프가
 * 됐다(실측 - 서버 기동 직후부터 계속 재발). {@value #FAILURE_BACKOFF_SECONDS}초
 * 백오프({@link #retryNotBefore})로 실패 시 재시도 간격 자체를 강제해 이를
 * 막는다 - 성공 시엔 기존대로 하루 단위 캐시를 그대로 쓴다. 백오프 값은
 * "틱마다 재호출하지 않을 만큼만" 넘기면 충분해(100ms 틱 대비 3초면 이미
 * 30배 완화) 로컬 개발처럼 재시작이 잦아 캐시 미스가 자주 나는 환경에서도
 * 매번 오래 기다리지 않도록 짧게 잡는다 - 다른 Toss 레이트리밋 백오프
 * (OhlcvCollectorScheduler 등)와 동일하게 3초로 통일.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarCache {

    private static final long FAILURE_BACKOFF_SECONDS = 3;

    private final TossApiClient tossApiClient;

    private volatile LocalDate cachedDate = LocalDate.MIN;
    private volatile List<TossMarketCalendarResponse.MarketSession> tradingSessions = List.of();
    private volatile Instant retryNotBefore = Instant.MIN;

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
        if (Instant.now().isBefore(retryNotBefore)) {
            return; // 최근 실패로 백오프 중 - 매 틱 재호출 방지
        }
        TossMarketCalendarResponse response;
        try {
            response = tossApiClient.getMarketCalendar();
        } catch (Exception e) {
            retryNotBefore = Instant.now().plusSeconds(FAILURE_BACKOFF_SECONDS);
            log.warn("장 운영 캘린더 캐시 미스(date={}) 갱신 실패, {}초간 재시도 보류: error={}",
                today, FAILURE_BACKOFF_SECONDS, e.getMessage());
            return;
        }
        if (response == null || response.result() == null || response.result().today() == null) {
            log.warn("장 운영 캘린더 응답이 비어있어 장중이 아닌 것으로 처리: date={}", today);
            this.tradingSessions = List.of();
            this.cachedDate = today;
            return;
        }
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
