package com.quantlime.price.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossOrderbookResponse;
import com.quantlime.infra.toss.dto.TossPriceLimitResponse;
import com.quantlime.infra.toss.dto.TossStockWarningResponse;
import com.quantlime.infra.toss.dto.TossTradeResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 종목 상세의 호가·체결·상하한가·매수유의 조회를 종목+종류별 짧은 TTL로
 * 캐싱한다. 호가/체결은 종목 상세 화면이 몇 초마다 폴링하므로 TTL 없이
 * 그대로 토스에 흘리면 같은 종목을 보는 사용자 수만큼 호출이 늘어 시세
 * 스윕과 공유하는 MARKET_DATA 호출 예산을 갉아먹는다 - 같은 종목은 TTL
 * 안에서 1회만 호출한다.
 *
 * <p>갱신에 실패하면 만료된 이전 값이 있을 때 그걸 그대로 돌려준다(stale-serve,
 * {@link com.quantlime.market.cache.TossMarketRankingCache}와 동일한 판단 - 화면이
 * 통째로 비는 것보다 몇 초 지난 호가가 낫다). 이전 값조차 없으면 예외를 그대로
 * 전파한다. 단일 인스턴스 배포를 전제로 JVM 로컬 캐시로 충분하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMarketDepthCache {

    static final Duration ORDERBOOK_TTL = Duration.ofSeconds(2);
    static final Duration TRADES_TTL = Duration.ofSeconds(2);
    static final Duration PRICE_LIMIT_TTL = Duration.ofMinutes(1);
    static final Duration WARNINGS_TTL = Duration.ofMinutes(5);
    static final int TRADES_COUNT = 30;
    // 종목 수 × 종류 4개가 무한정 쌓이지 않게 하는 상한 - 넘으면 만료분부터 정리하고,
    // 그래도 넘으면 전부 비운다(캐시는 언제든 다시 채워지는 값이라 단순함을 택했다).
    private static final int MAX_ENTRIES = 4000;

    private final TossApiClient tossApiClient;

    private final Map<Key, Entry> cache = new ConcurrentHashMap<>();

    public TossOrderbookResponse.Orderbook orderbook(String symbol) {
        return load(Kind.ORDERBOOK, symbol, ORDERBOOK_TTL, () -> tossApiClient.getOrderbook(symbol).result());
    }

    public TossTradeResponse trades(String symbol) {
        return load(Kind.TRADES, symbol, TRADES_TTL, () -> tossApiClient.getTrades(symbol, TRADES_COUNT));
    }

    public TossPriceLimitResponse.PriceLimit priceLimit(String symbol) {
        return load(Kind.PRICE_LIMIT, symbol, PRICE_LIMIT_TTL, () -> tossApiClient.getPriceLimits(symbol).result());
    }

    public TossStockWarningResponse warnings(String symbol) {
        return load(Kind.WARNINGS, symbol, WARNINGS_TTL, () -> tossApiClient.getStockWarnings(symbol));
    }

    @SuppressWarnings("unchecked")
    private <T> T load(Kind kind, String symbol, Duration ttl, Supplier<T> loader) {
        Key key = new Key(kind, symbol);
        Entry entry = cache.get(key);
        if (entry != null && entry.isFresh(ttl)) {
            return (T) entry.value();
        }
        try {
            T value = loader.get();
            evictIfOverCapacity();
            cache.put(key, new Entry(value, Instant.now()));
            return value;
        } catch (RuntimeException e) {
            if (entry != null) {
                log.warn("토스 {} 조회 실패, 이전 캐시로 폴백: symbol={}, error={}", kind, symbol, e.getMessage());
                return (T) entry.value();
            }
            throw e;
        }
    }

    private void evictIfOverCapacity() {
        if (cache.size() < MAX_ENTRIES) {
            return;
        }
        Instant cutoff = Instant.now().minus(WARNINGS_TTL);
        cache.entrySet().removeIf(e -> e.getValue().cachedAt().isBefore(cutoff));
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
    }

    private enum Kind {
        ORDERBOOK, TRADES, PRICE_LIMIT, WARNINGS
    }

    private record Key(Kind kind, String symbol) {
    }

    private record Entry(Object value, Instant cachedAt) {

        boolean isFresh(Duration ttl) {
            return cachedAt.plus(ttl).isAfter(Instant.now());
        }
    }
}
