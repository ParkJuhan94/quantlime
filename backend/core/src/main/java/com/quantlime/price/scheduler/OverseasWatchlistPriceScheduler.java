package com.quantlime.price.scheduler;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.util.ChangeRateCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 해외(NASDAQ/NYSE) 관심종목의 실시간가를 폴링해 Redis({@link PriceCacheStore})에
 * 적재하고 즉시 STOMP로 브로드캐스트한다. 국내
 * ({@code DomesticMarketPriceSweepScheduler} + {@code DomesticWatchlistPriceRelayScheduler})와
 * 달리 "스윕(전종목)"과 "릴레이(캐시 중계)"를 분리하지 않고 한 스케줄러가
 * 둘 다 한다 - 해외는 전종목 스윕 자체가 없고(랭킹은
 * {@code TossMarketRankingCache}가 전담), 관심종목만 대상이라 규모가
 * 작아 굳이 나눌 필요가 없다(2026-07-29, ROADMAP #10 - KIS 웹소켓
 * 대신 기존 Toss 폴링 인프라를 해외로 확장).
 *
 * <p>Toss `/api/v1/prices`가 해외 티커도 지원한다는 걸 이 세션에서
 * 실제 라이브 호출로 확인했다({@code TossApiClient.getCurrentPrices}
 * 재사용, 새 외부 연동 불필요).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverseasWatchlistPriceScheduler {

    private static final String PRICE_TOPIC_PREFIX = "/topic/price/";
    private static final int TOSS_BATCH_SIZE = 200;

    private final OverseasMarketCalendarCache overseasMarketCalendarCache;
    // 필드명이 PriceCacheConfig의 @Bean 메서드명과 일치해야 Spring이 같은
    // 타입(WatchlistedStockCodeCache)의 두 Bean 중 이걸 고른다(By-Name
    // 디스앰비규에이션).
    private final WatchlistedStockCodeCache overseasWatchlistedStockCodeCache;
    // 필드명이 PriceCacheConfig의 @Bean 메서드명(overseasPreviousCloseCache)과
    // 일치해야 Spring이 같은 타입(PreviousCloseCache)의 두 Bean 중 이걸
    // 고른다(MarketDataRefreshTaskExecutorConfig의 TaskExecutor 2개와
    // 동일한 By-Name 디스앰비규에이션 관례 - @Qualifier 없이도 동작).
    private final PreviousCloseCache overseasPreviousCloseCache;
    private final TossApiClient tossApiClient;
    private final PriceCacheStore priceCacheStore;
    private final SimpMessagingTemplate messagingTemplate;

    // 전용 풀(SchedulerConfig.priceSweepTaskScheduler)에서 실행 - 사유는
    // DomesticMarketPriceSweepScheduler 참고(2026-08-17).
    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}",
        scheduler = "priceSweepTaskScheduler")
    public void refreshAndBroadcast() {
        SafeExecutor.runSafely("해외 관심종목 실시간가 갱신", this::refreshOnce);
    }

    private void refreshOnce() {
        if (!overseasMarketCalendarCache.isMarketOpenNow()) {
            return;
        }

        List<String> stockCodes = overseasWatchlistedStockCodeCache.get();
        if (stockCodes.isEmpty()) {
            return;
        }

        Map<String, Double> previousCloseByCode = overseasPreviousCloseCache.get(stockCodes);
        for (List<String> chunkCodes : chunk(stockCodes, TOSS_BATCH_SIZE)) {
            fetchAndBroadcastChunk(chunkCodes, previousCloseByCode);
        }
    }

    private void fetchAndBroadcastChunk(List<String> chunkCodes, Map<String, Double> previousCloseByCode) {
        TossPriceResponse response;
        try {
            response = tossApiClient.getCurrentPrices(String.join(",", chunkCodes));
        } catch (ExternalApiException e) {
            log.warn("해외 관심종목 시세 조회 실패(다음 틱에 재시도): error={}", e.getMessage());
            return;
        }

        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null) {
            return;
        }

        // 종목당 개별 save 대신 청크 전체를 파이프라인 하나로 저장한다
        // (2026-08-19, PriceCacheStore.saveAll 참고) - 브로드캐스트는 종목별로
        // 토픽이 달라 그대로 개별 전송한다.
        List<PriceSnapshot> snapshots = new ArrayList<>();
        for (TossPriceResponse.TossPrice price : prices) {
            PriceSnapshot snapshot = toSnapshot(price, previousCloseByCode);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        priceCacheStore.saveAll(snapshots);
        for (PriceSnapshot snapshot : snapshots) {
            messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + snapshot.stockCode(), snapshot);
        }
    }

    private PriceSnapshot toSnapshot(TossPriceResponse.TossPrice price, Map<String, Double> previousCloseByCode) {
        if (!StringUtils.hasText(price.lastPrice())) {
            return null;
        }
        Double currentPrice = parseLastPrice(price.lastPrice());
        if (currentPrice == null) {
            // 국내 스윕(DomesticMarketPriceSweepScheduler)과 동일한 설계 철학 -
            // 해당 종목만 스킵하고 나머지는 계속 처리한다.
            log.warn("해외 현재가 파싱 실패로 해당 종목만 스킵: symbol={}, lastPrice={}",
                price.symbol(), price.lastPrice());
            return null;
        }
        Double previousClose = previousCloseByCode.get(price.symbol());
        Double changeRate = ChangeRateCalculator.calculate(currentPrice, previousClose);
        return new PriceSnapshot(price.symbol(), currentPrice, changeRate, price.timestamp());
    }

    private Double parseLastPrice(String lastPrice) {
        try {
            return Double.parseDouble(lastPrice.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<List<String>> chunk(List<String> items, int size) {
        int chunkCount = (items.size() + size - 1) / size;
        return IntStream.range(0, chunkCount)
            .mapToObj(i -> items.subList(i * size, Math.min(items.size(), (i + 1) * size)))
            .toList();
    }
}
