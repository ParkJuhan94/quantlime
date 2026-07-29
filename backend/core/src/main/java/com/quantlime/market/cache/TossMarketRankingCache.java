package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossRankingResponse;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Toss `/api/v1/rankings`(2026-07-29 신규 확인) 기반 랭킹 캐시. 국내·해외
 * 공용이며, 서버가 순위를 직접 계산해 주므로 {@code MarketPriceSweepScheduler}
 * 같은 전종목 스윕이 필요 없다 - (scope, sort) 조합별로 {@value #TTL_SECONDS}초
 * 온디맨드 TTL 캐시({@link MarketIndexCache}와 동일 패턴).
 *
 * <p>최대 100건까지만 온다는 API 자체 제약 때문에, "관심종목만 보기"
 * 필터는 이 캐시가 아니라 별도 자체 계산 경로({@code MarketRankingService}
 * 참고)를 쓴다 - 관심종목이 top100 밖에 있어도 걸러지지 않아야 하기
 * 때문(2026-07-29 결정, 기존 전종목 자체 계산 방식의 필터 정확도를
 * 그대로 보존하기 위함).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossMarketRankingCache {

    private static final int TTL_SECONDS = 10;
    private static final int MAX_COUNT = 100;

    private static final String MARKET_KR = "KR";
    private static final String MARKET_US = "US";

    private static final String TYPE_TOP_GAINERS = "TOP_GAINERS";
    private static final String TYPE_TOP_LOSERS = "TOP_LOSERS";
    private static final String TYPE_TRADING_AMOUNT = "MARKET_TRADING_AMOUNT";
    private static final String TYPE_TRADING_VOLUME = "MARKET_TRADING_VOLUME";

    private static final String DURATION_1D = "1d";
    private static final String DURATION_REALTIME = "realtime";

    public static final String SORT_GAINERS = "gainers";
    public static final String SORT_LOSERS = "losers";
    public static final String SORT_AMOUNT = "amount";
    public static final String SORT_VOLUME = "volume";

    public static final String SCOPE_DOMESTIC = "domestic";
    public static final String SCOPE_OVERSEAS = "overseas";

    private final TossApiClient tossApiClient;
    private final StockRepository stockRepository;

    private final Map<CacheKey, CachedEntry> cache = new ConcurrentHashMap<>();

    public List<MarketRankingResponse> get(String scope, String sort) {
        CacheKey key = new CacheKey(scope, sort);
        CachedEntry entry = cache.get(key);
        if (entry != null && !entry.isStale()) {
            return entry.items();
        }
        return refresh(key);
    }

    private synchronized List<MarketRankingResponse> refresh(CacheKey key) {
        CachedEntry existing = cache.get(key);
        if (existing != null && !existing.isStale()) {
            return existing.items(); // 락 대기 중 다른 스레드가 이미 갱신함
        }
        try {
            List<MarketRankingResponse> items = fetchAndMap(key);
            cache.put(key, new CachedEntry(items, Instant.now()));
            return items;
        } catch (Exception e) {
            // 실패해도 이전에 캐싱된 값(만료됐더라도)이 있으면 그걸 그대로
            // 반환 - 화면이 통째로 비는 것보다 약간 오래된 랭킹이 낫다.
            log.warn("Toss 랭킹 조회 실패, 이전 캐시로 폴백: scope={}, sort={}, error={}",
                key.scope(), key.sort(), e.getMessage());
            return existing != null ? existing.items() : List.of();
        }
    }

    private List<MarketRankingResponse> fetchAndMap(CacheKey key) {
        String marketCountry = SCOPE_OVERSEAS.equals(key.scope()) ? MARKET_US : MARKET_KR;
        String type = resolveType(key.sort());
        String duration = resolveDuration(key.sort());

        TossRankingResponse response = tossApiClient.getRankings(type, marketCountry, duration, MAX_COUNT);
        List<TossRankingResponse.RankingItem> rankings =
            response.result() == null || response.result().rankings() == null
                ? List.of() : response.result().rankings();
        if (rankings.isEmpty()) {
            return List.of();
        }

        Map<String, Stock> stockByCode = stockRepository
            .findByStockCodeIn(rankings.stream().map(TossRankingResponse.RankingItem::symbol).toList())
            .stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity(), (a, b) -> a));

        return rankings.stream()
            .map(item -> toResponse(item, stockByCode))
            .toList();
    }

    private String resolveType(String sort) {
        return switch (sort) {
            case SORT_LOSERS -> TYPE_TOP_LOSERS;
            case SORT_AMOUNT -> TYPE_TRADING_AMOUNT;
            case SORT_VOLUME -> TYPE_TRADING_VOLUME;
            default -> TYPE_TOP_GAINERS;
        };
    }

    /**
     * TOP_GAINERS/TOP_LOSERS는 duration=realtime을 지원하지 않는다(400
     * unsupported-ranking-duration) - 대신 1d를 쓰면 basePrice가 "1일 전
     * 시작 시점 기준가"라 사실상 기존 자체 계산 방식의 "전일종가 대비
     * 등락률"과 동등하다. amount/volume은 실시간 누적값이 자연스러우므로
     * realtime을 쓴다.
     */
    private String resolveDuration(String sort) {
        return (SORT_AMOUNT.equals(sort) || SORT_VOLUME.equals(sort)) ? DURATION_REALTIME : DURATION_1D;
    }

    private MarketRankingResponse toResponse(TossRankingResponse.RankingItem item, Map<String, Stock> stockByCode) {
        Stock stock = stockByCode.get(item.symbol());
        // 로컬 stock 테이블에 없는 심볼(해외 상위 종목이 백테스트 유니버스
        // 밖이거나, 국내라도 ELW/스팩 등 표준 6자리 코드가 아닌 경우)은
        // 종목명 자리에 심볼 원문을 그대로 보여준다 - 항목을 숨기지 않고
        // 실제 심볼을 정직하게 보여준다("없는 데이터를 꾸며내지 않는다"
        // 원칙 - 이름을 지어내는 게 아니라 원본 값을 그대로 노출).
        String stockName = stock != null ? stock.getStockName() : item.symbol();
        String sector = stock != null ? stock.getSector() : null;

        TossRankingResponse.RankingPrice price = item.price();
        return new MarketRankingResponse(
            item.symbol(),
            stockName,
            sector,
            parseDecimal(price.lastPrice()),
            parseChangeRate(price.changeRate()),
            item.currency(),
            parseDecimal(item.tradingVolume()),
            parseDecimal(item.tradingAmount()));
    }

    /** Toss changeRate는 소수 비율(0.0125=1.25%) - 기존 자체 계산 등락률
     * (퍼센트 숫자, 예: 1.25)과 단위를 맞추기 위해 ×100 한다. basePrice가
     * 0이면 null로 내려온다(스펙 명시). */
    private Double parseChangeRate(String changeRate) {
        return changeRate == null ? null : Double.parseDouble(changeRate) * 100.0;
    }

    private Double parseDecimal(String raw) {
        return raw == null ? null : Double.parseDouble(raw);
    }

    private record CacheKey(String scope, String sort) {
    }

    private record CachedEntry(List<MarketRankingResponse> items, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
