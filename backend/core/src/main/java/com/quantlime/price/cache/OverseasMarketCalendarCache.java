package com.quantlime.price.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse;
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
 * 미국 장 운영 여부(정규장 기준)를 캘린더 날짜 기준으로 캐싱한다.
 * {@link DomesticMarketCalendarCache}(국내)와 동일한 하루 1회 갱신 + 실패 백오프
 * 패턴이지만, 응답 형태가 달라 별도 클래스로 둔다.
 *
 * <p><b>미국 정규장은 KST 22:30~다음날 05:00으로 자정을 넘긴다</b> -
 * "오늘" 하루치 세션만 보면 05:00 이전(예: 새벽 3시) 구간에서 "장이
 * 닫혔다"고 잘못 판정한다. 그래서 Toss 응답이 주는 3영업일
 * (previousBusinessDay/today/nextBusinessDay) 전체의 regularMarket
 * 세션을 모두 모아두고, 지금이 그중 하나에 속하는지로 판정한다.
 *
 * <p>주간거래(dayMarket)는 v1 스코프에서 제외한다(정규장만 게이트로
 * 사용) - 미국 주간거래는 프리/애프터마켓과 별개로 별도 심볼 접두가
 * 필요한 KIS 얘기였고 Toss 심볼 형식은 국내/해외 동일하므로 그 문제는
 * 없지만, "주간거래 시간까지 실시간가를 켜둘 필요가 있는가"는 별도
 * 판단이 필요해 우선 보류한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverseasMarketCalendarCache {

    private static final long FAILURE_BACKOFF_SECONDS = 3;

    private final TossApiClient tossApiClient;

    private volatile LocalDate cachedDate = LocalDate.MIN;
    private volatile List<TossMarketCalendarResponse.MarketSession> regularMarketSessions = List.of();
    private volatile Instant retryNotBefore = Instant.MIN;

    public boolean isMarketOpenNow() {
        LocalDate today = LocalDate.now();
        if (!cachedDate.equals(today)) {
            refresh(today);
        }
        if (regularMarketSessions.isEmpty()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        return regularMarketSessions.stream().anyMatch(session -> isWithin(now, session));
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
        TossUsMarketCalendarResponse response;
        try {
            response = tossApiClient.getUsMarketCalendar();
        } catch (Exception e) {
            retryNotBefore = Instant.now().plusSeconds(FAILURE_BACKOFF_SECONDS);
            log.warn("해외 장 운영 캘린더 캐시 미스(date={}) 갱신 실패, {}초간 재시도 보류: error={}",
                today, FAILURE_BACKOFF_SECONDS, e.getMessage());
            return;
        }
        if (response == null || response.result() == null) {
            log.warn("해외 장 운영 캘린더 응답이 비어있어 장중이 아닌 것으로 처리: date={}", today);
            this.regularMarketSessions = List.of();
            this.cachedDate = today;
            return;
        }
        TossUsMarketCalendarResponse.UsMarketCalendarResult result = response.result();
        this.regularMarketSessions = Stream.of(
                result.previousBusinessDay(), result.today(), result.nextBusinessDay())
            .filter(Objects::nonNull)
            .map(TossUsMarketCalendarResponse.UsMarketDay::regularMarket)
            .filter(Objects::nonNull)
            .toList();
        this.cachedDate = today;
        log.info("해외 장 운영 캘린더 갱신: date={}, 세션수={}", today, this.regularMarketSessions.size());
    }
}
