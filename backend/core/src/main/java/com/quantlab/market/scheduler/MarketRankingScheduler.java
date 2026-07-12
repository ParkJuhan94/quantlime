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
import com.quantlab.stock.domain.Stock;
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
 * 전종목(약 2,700개)의 등락률을 짧은 주기로 폴링해 급등락 랭킹을
 * 계산한다. {@code PriceBroadcastScheduler}(관심종목 전용, 3초 주기)와
 * 동일한 Toss {@code MARKET_DATA} Rate Limit 그룹 예산을 나눠 쓰므로
 * 기본 주기를 더 길게(5초) 둔다 - docs/ROADMAP.md #2a 참고
 * (14호출/스윕 ≈ 1.4초, 3~5초 주기면 사실상 실시간).
 *
 * <p>청크 하나가 429 등으로 실패해도 전체 스윕을 중단하지 않고 그 청크만
 * 건너뛴다 - 다음 틱에 다시 시도되므로 일부 종목의 순위가 한 틱만큼
 * 지연되는 정도로 그친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketRankingScheduler {

    private static final int TOSS_BATCH_SIZE = 200;

    private final MarketCalendarCache marketCalendarCache;
    private final AllListedStockCache allListedStockCache;
    private final PreviousCloseCache previousCloseCache;
    private final MarketRankingCache marketRankingCache;
    private final TossApiClient tossApiClient;

    @Scheduled(fixedDelayString = "${market-ranking.poll-interval-ms:5000}")
    public void refreshRanking() {
        SafeExecutor.runSafely("전종목 급등락 랭킹 갱신", this::refreshOnce);
    }

    private void refreshOnce() {
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
        for (List<String> chunk : chunk(stockCodes, TOSS_BATCH_SIZE)) {
            ranking.addAll(fetchChunkRanking(chunk, stockByCode, previousCloseByStockCode));
        }
        marketRankingCache.update(ranking);
    }

    private List<MarketRankingResponse> fetchChunkRanking(
        List<String> chunkCodes, Map<String, Stock> stockByCode, Map<String, Long> previousCloseByStockCode) {
        TossPriceResponse response;
        try {
            response = tossApiClient.getCurrentPrices(String.join(",", chunkCodes));
        } catch (ExternalApiException e) {
            log.warn("전종목 랭킹 조회 중 일부 청크 스킵(다음 틱에 재시도): error={}", e.getMessage());
            return List.of();
        }

        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null) {
            return List.of();
        }

        List<MarketRankingResponse> result = new ArrayList<>();
        for (TossPriceResponse.TossPrice price : prices) {
            MarketRankingResponse ranked = toRanking(price, stockByCode, previousCloseByStockCode);
            if (ranked != null) {
                result.add(ranked);
            }
        }
        return result;
    }

    private MarketRankingResponse toRanking(
        TossPriceResponse.TossPrice price, Map<String, Stock> stockByCode,
        Map<String, Long> previousCloseByStockCode) {
        if (!StringUtils.hasText(price.lastPrice())) {
            return null;
        }
        Stock stock = stockByCode.get(price.symbol());
        Long previousClose = previousCloseByStockCode.get(price.symbol());
        if (stock == null || previousClose == null || previousClose == 0) {
            return null;
        }
        Long currentPrice = Long.parseLong(price.lastPrice());
        double changeRate = (currentPrice - previousClose) * 100.0 / previousClose;
        return new MarketRankingResponse(
            stock.getStockCode(), stock.getStockName(), stock.getSector(), currentPrice, changeRate);
    }

    private List<List<String>> chunk(List<String> items, int size) {
        int chunkCount = (items.size() + size - 1) / size;
        return IntStream.range(0, chunkCount)
            .mapToObj(i -> items.subList(i * size, Math.min(items.size(), (i + 1) * size)))
            .toList();
    }
}
