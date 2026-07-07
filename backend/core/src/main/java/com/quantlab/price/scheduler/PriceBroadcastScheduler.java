package com.quantlab.price.scheduler;

import com.quantlab.common.util.SafeExecutor;
import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.cache.MarketCalendarCache;
import com.quantlab.price.cache.PreviousCloseCache;
import com.quantlab.price.cache.PriceCacheStore;
import com.quantlab.price.cache.WatchlistedStockCodeCache;
import com.quantlab.price.dto.response.PriceBroadcastMessage;
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
 * 관심 종목의 실시간(에 가까운) 시세를 짧은 주기로 폴링해 STOMP로
 * 브로드캐스트한다. 토스증권 API가 아직 WebSocket을 지원하지 않으므로
 * (toss-openapi.json "Market Data" 태그: "웹 소켓은 추후 지원 예정입니다")
 * REST 폴링 → WebSocket 변환 계층으로 구현한다.
 *
 * <p>정상 상태(steady state)에서는 틱마다 MySQL 쿼리가 발생하지 않는다 -
 * 장중 판별/관심종목 목록/전일종가를 각각 {@link MarketCalendarCache},
 * {@link WatchlistedStockCodeCache}, {@link PreviousCloseCache}로
 * 캐싱해, 실제로 값이 바뀔 때만(하루 1회, 관심종목 변경 시 등) DB를
 * 다시 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceBroadcastScheduler {

    private static final String PRICE_TOPIC_PREFIX = "/topic/price/";
    private static final int TOSS_BATCH_SIZE = 200; // getCurrentPrices 최대 다건 조회 수

    private final MarketCalendarCache marketCalendarCache;
    private final WatchlistedStockCodeCache watchlistedStockCodeCache;
    private final PreviousCloseCache previousCloseCache;
    private final PriceCacheStore priceCacheStore;
    private final TossApiClient tossApiClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}")
    public void broadcastCurrentPrices() {
        SafeExecutor.runSafely("실시간 시세 브로드캐스트", this::broadcastOnce);
    }

    private void broadcastOnce() {
        if (!marketCalendarCache.isMarketOpenNow()) {
            return;
        }

        List<String> stockCodes = watchlistedStockCodeCache.get();
        if (stockCodes.isEmpty()) {
            return;
        }

        Map<String, Long> previousCloseByStockCode = previousCloseCache.get(stockCodes);
        for (List<String> chunk : chunk(stockCodes, TOSS_BATCH_SIZE)) {
            broadcastChunk(chunk, previousCloseByStockCode);
        }
    }

    private void broadcastChunk(List<String> stockCodes, Map<String, Long> previousCloseByStockCode) {
        TossPriceResponse response = tossApiClient.getCurrentPrices(String.join(",", stockCodes));
        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null) {
            return;
        }

        for (TossPriceResponse.TossPrice price : prices) {
            PriceBroadcastMessage message = toBroadcastMessage(price, previousCloseByStockCode);
            if (message == null) {
                continue;
            }
            priceCacheStore.save(message);
            messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + message.stockCode(), message);
        }
    }

    private PriceBroadcastMessage toBroadcastMessage(
        TossPriceResponse.TossPrice price, Map<String, Long> previousCloseByStockCode) {
        if (!StringUtils.hasText(price.lastPrice())) {
            return null;
        }
        Long currentPrice = Long.parseLong(price.lastPrice());
        Long previousClose = previousCloseByStockCode.get(price.symbol());
        Double changeRate = calculateChangeRate(currentPrice, previousClose);
        return new PriceBroadcastMessage(price.symbol(), currentPrice, changeRate, price.timestamp());
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
