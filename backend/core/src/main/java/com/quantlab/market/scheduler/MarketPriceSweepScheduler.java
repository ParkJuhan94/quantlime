package com.quantlab.market.scheduler;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.common.util.SafeExecutor;
import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.market.cache.AllListedStockCache;
import com.quantlab.market.cache.MarketRankingCache;
import com.quantlab.market.dto.response.MarketRankingResponse;
import com.quantlab.price.cache.MarketCalendarCache;
import com.quantlab.price.cache.PreviousCloseCache;
import com.quantlab.price.cache.PriceCacheStore;
import com.quantlab.price.dto.response.PriceSnapshot;
import com.quantlab.stock.domain.Stock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 전종목(약 2,700개)의 현재가를 짧은 주기로 폴링해 (1) 급등락 랭킹을
 * 계산하고 (2) 종목별 최신 시세를 {@link PriceCacheStore}(Redis)에
 * 적재하는 유일한 가격 조회 파이프라인이다.
 *
 * <p>원래는 {@code WatchlistPriceRelayScheduler}(관심종목 전용, 당시 이름은
 * {@code PriceBroadcastScheduler})와 이 스케줄러가 각각 독립적으로 Toss를
 * 호출해, 관심종목이면서 전종목이기도 한 종목의 가격이 3~5초 창 안에서
 * 두 번 조회되는 구조였다. Toss {@code MARKET_DATA} 예산을 한 파이프라인으로
 * 통합하기 위해 이 스케줄러를 유일한 가격 조회원으로 두고,
 * {@code WatchlistPriceRelayScheduler}는 Toss를 직접 호출하지 않고 여기서
 * 저장한 Redis 스냅샷만 읽어 STOMP로 중계하도록 재설계했다(2026-07-15).
 * 이름도 "랭킹 계산기"가 아니라 "전종목 가격 스윕 파이프라인"이라는
 * 실제 역할에 맞춰 {@code MarketRankingScheduler}에서 변경함(2026-07-16).
 *
 * <p>청크(최대 14개) 사이에 {@link #TOSS_API_DELAY_MS}(120ms) 딜레이를
 * 둔다 - 딜레이 없이 연속 호출하면 스윕 하나가 순식간에 초당 토큰 버킷
 * (스펙 예시 10건/초)을 넘겨 429를 유발할 수 있음을 실제 운영 로그로
 * 확인 후 추가함(2026-07-13). 120ms는 네트워크 지연이 0이어도 초당
 * 8.33건로 여유를 두는 값 - 네트워크 왕복시간이 주는 여유는 별도
 * 안전마진으로 계산에 넣지 않는다(그 가정에 기대는 것 자체가 애초에
 * 429를 유발했던 무딜레이 설계와 같은 함정이라 판단).
 *
 * <p>청크 하나가 429 등으로 실패해도 전체 스윕을 중단하지 않고 그 청크만
 * 건너뛴다 - 다음 틱에 다시 시도되므로 일부 종목의 순위/시세가 한 틱만큼
 * 지연되는 정도로 그친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceSweepScheduler {

    private static final int TOSS_BATCH_SIZE = 200;
    private static final long TOSS_API_DELAY_MS = 120;

    // 스윕 1회 소요시간(폴링 주기 100ms를 못 따라가는지) + 청크 스킵
    // 횟수(429 등으로 그 틱의 일부 종목이 갱신되지 못한 횟수)를 계측한다.
    private static final String METRIC_SWEEP_DURATION = "market.sweep.duration";
    private static final String METRIC_CHUNK_SKIPPED = "market.sweep.chunk.skipped";

    private final MarketCalendarCache marketCalendarCache;
    private final AllListedStockCache allListedStockCache;
    private final PreviousCloseCache previousCloseCache;
    private final MarketRankingCache marketRankingCache;
    private final TossApiClient tossApiClient;
    private final PriceCacheStore priceCacheStore;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${market-ranking.poll-interval-ms:100}")
    public void refreshRanking() {
        SafeExecutor.runSafely("전종목 시세/랭킹 갱신", this::refreshOnce);
    }

    private void refreshOnce() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            doRefresh();
        } finally {
            sample.stop(meterRegistry.timer(METRIC_SWEEP_DURATION));
        }
    }

    private void doRefresh() {
        if (!marketCalendarCache.isMarketOpenNow()) {
            return;
        }

        List<Stock> stocks = allListedStockCache.get();
        if (stocks.isEmpty()) {
            return;
        }

        Map<String, Stock> stockByCode = stocks.stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity(), (a, b) -> a));
        List<String> stockCodes = new ArrayList<>(stockByCode.keySet());
        Map<String, Long> previousCloseByStockCode = previousCloseCache.get(stockCodes);

        List<MarketRankingResponse> ranking = new ArrayList<>();
        List<List<String>> chunks = chunk(stockCodes, TOSS_BATCH_SIZE);
        for (int i = 0; i < chunks.size(); i++) {
            ranking.addAll(fetchChunk(chunks.get(i), stockByCode, previousCloseByStockCode));
            if (i < chunks.size() - 1 && !sleepBetweenChunks()) {
                break;
            }
        }
        marketRankingCache.update(ranking);
    }

    private boolean sleepBetweenChunks() {
        try {
            Thread.sleep(TOSS_API_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("전종목 시세 갱신 중단: 인터럽트 발생");
            return false;
        }
    }

    private List<MarketRankingResponse> fetchChunk(
        List<String> chunkCodes, Map<String, Stock> stockByCode, Map<String, Long> previousCloseByStockCode) {
        TossPriceResponse response;
        try {
            response = tossApiClient.getCurrentPrices(String.join(",", chunkCodes));
        } catch (ExternalApiException e) {
            log.warn("전종목 시세 조회 중 일부 청크 스킵(다음 틱에 재시도): error={}", e.getMessage());
            meterRegistry.counter(METRIC_CHUNK_SKIPPED, "reason", e.getCode()).increment();
            return List.of();
        }

        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null) {
            return List.of();
        }

        List<MarketRankingResponse> result = new ArrayList<>();
        for (TossPriceResponse.TossPrice price : prices) {
            MarketRankingResponse ranked = cacheAndRank(price, stockByCode, previousCloseByStockCode);
            if (ranked != null) {
                result.add(ranked);
            }
        }
        return result;
    }

    /**
     * 심볼 하나의 시세를 (previousClose 유무와 무관하게) Redis에 항상
     * 적재하고, 랭킹에는 등락률을 계산할 수 있는 경우에만 포함한다 -
     * {@code WatchlistPriceRelayScheduler}가 이 캐시를 그대로 브로드캐스트하므로,
     * 관심종목인데 전일종가가 없는 경우에도 시세 자체는 화면에 보여야
     * 한다(과거 PriceBroadcastScheduler.toBroadcastMessage와 동일한 규칙).
     */
    private MarketRankingResponse cacheAndRank(
        TossPriceResponse.TossPrice price, Map<String, Stock> stockByCode,
        Map<String, Long> previousCloseByStockCode) {
        if (!StringUtils.hasText(price.lastPrice())) {
            return null;
        }
        Long currentPrice = Long.parseLong(price.lastPrice());
        Long previousClose = previousCloseByStockCode.get(price.symbol());
        Double changeRate = calculateChangeRate(currentPrice, previousClose);
        priceCacheStore.save(new PriceSnapshot(price.symbol(), currentPrice, changeRate, price.timestamp()));

        Stock stock = stockByCode.get(price.symbol());
        if (stock == null || changeRate == null) {
            return null;
        }
        return new MarketRankingResponse(stock.getStockCode(), stock.getStockName(), stock.getSector(), currentPrice, changeRate);
    }

    private Double calculateChangeRate(Long currentPrice, Long previousClose) {
        if (previousClose == null || previousClose == 0) {
            return null;
        }
        return (currentPrice - previousClose) * 100.0 / previousClose;
    }

    private List<List<String>> chunk(List<String> items, int size) {
        int chunkCount = (items.size() + size - 1) / size;
        return IntStream.range(0, chunkCount)
            .mapToObj(i -> items.subList(i * size, Math.min(items.size(), (i + 1) * size)))
            .toList();
    }
}
