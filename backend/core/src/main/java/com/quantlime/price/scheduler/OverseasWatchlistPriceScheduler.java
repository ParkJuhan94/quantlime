package com.quantlime.price.scheduler;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.util.ChangeRateCalculator;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.mapper.StockMapper;
import com.quantlime.stock.repository.StockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 해외(NASDAQ/NYSE) 관심종목의 실시간가를 폴링해 Redis({@link PriceCacheStore})에
 * 적재하고 즉시 STOMP로 브로드캐스트한다. 국내
 * ({@code DomesticMarketPriceSweepScheduler} + {@code DomesticWatchlistPriceRelayScheduler})와
 * 달리 "스윕(전종목)"과 "릴레이(캐시 중계)"를 분리하지 않고 한 스케줄러가
 * 둘 다 한다 - 해외는 전종목 스윕 자체가 없고(top-N 공개 랭킹은
 * {@code TossMarketRankingCache}가 전담), 관심종목만 대상이라 규모가
 * 작아 굳이 나눌 필요가 없다(2026-07-29, ROADMAP #10 - KIS 웹소켓
 * 대신 기존 Toss 폴링 인프라를 해외로 확장). "관심종목만 보기" 등락률
 * 랭킹(gainers/losers)은 이 스케줄러가 매 틱 계산해 {@link MarketRankingCache}에
 * 쓴다(2026-09-25 - 이전엔 {@code MarketRankingService}가 매 HTTP 요청마다
 * 직접 계산했는데, 국내와 동일한 "리더가 계산 → Redis에 씀" 패턴으로
 * 통일했다).
 *
 * <p>Toss `/api/v1/prices`가 해외 티커도 지원한다는 걸 이 세션에서
 * 실제 라이브 호출로 확인했다({@code TossApiClient.getCurrentPrices}
 * 재사용, 새 외부 연동 불필요).
 */
// 로컬 scale-out 검증(SyntheticPriceFeedScheduler, price-feed.mode=synthetic)
// 에서는 이 클래스 전체를 끈다 - 해외는 스윕/릴레이가 이 클래스 하나에
// 묶여 있어(위 javadoc 참고), synthetic 모드에서는 SyntheticPriceFeedScheduler가
// 시세 저장+브로드캐스트+랭킹 갱신을 전부 대신한다(2026-09-25). 기본값
// (미지정)은 기존 그대로 Toss를 호출.
@Slf4j
@Component
@ConditionalOnProperty(name = "price-feed.mode", havingValue = "toss", matchIfMissing = true)
@RequiredArgsConstructor
public class OverseasWatchlistPriceScheduler {

    // 점(.) 구분자 채택 이유는 DomesticWatchlistPriceRelayScheduler의 동일
    // 상수 주석 참고(RabbitMQ STOMP relay 제약, 2026-09-25).
    private static final String PRICE_TOPIC_PREFIX = "/topic/price.";
    private static final int TOSS_BATCH_SIZE = 200;
    // MarketRankingResponse.currency - 해외 전용 경로라 항상 USD(예전
    // MarketRankingService.overseasWatchlistRanking과 동일 규칙).
    private static final String CURRENCY_USD = "USD";

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
    // 필드명이 MarketRankingCacheConfig의 @Bean 메서드명과 일치해야
    // Spring이 같은 타입(MarketRankingCache)의 두 Bean 중 이걸 고른다
    // (By-Name 디스앰비규에이션 - 위 두 필드와 동일 관례).
    private final MarketRankingCache overseasMarketRankingCache;
    private final TossApiClient tossApiClient;
    private final PriceCacheStore priceCacheStore;
    private final StockRepository stockRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PriceRelayLeaderGate priceRelayLeaderGate;

    // 전용 풀(SchedulerConfig.priceSweepTaskScheduler)에서 실행 - 사유는
    // DomesticMarketPriceSweepScheduler 참고(2026-08-17).
    //
    // 리더 인스턴스에서만 돈다(2026-09-25, PriceRelayLeaderGate 참고 -
    // DomesticWatchlistPriceRelayScheduler와 동일 이유).
    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}",
        scheduler = "priceSweepTaskScheduler")
    public void refreshAndBroadcast() {
        if (!priceRelayLeaderGate.isLeader()) {
            return;
        }
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
        // "관심종목만 보기" 랭킹에 종목명/섹터/로고가 필요해(MarketRankingResponse
        // 참고) Stock 메타데이터를 한 번만 조회해둔다 - 전종목 스윕(domestic)의
        // DomesticListedStockCache와 달리 해외는 관심종목만 대상이라(규모가
        // 작음) 캐시 없이 매 틱 직접 조회해도 무해하다.
        Map<String, Stock> stockByCode = stockRepository.findByStockCodeIn(stockCodes).stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity(), (a, b) -> a));

        List<MarketRankingResponse> ranking = new ArrayList<>();
        for (List<String> chunkCodes : chunk(stockCodes, TOSS_BATCH_SIZE)) {
            ranking.addAll(fetchAndBroadcastChunk(chunkCodes, previousCloseByCode, stockByCode));
        }
        overseasMarketRankingCache.update(ranking);
    }

    private List<MarketRankingResponse> fetchAndBroadcastChunk(
            List<String> chunkCodes, Map<String, Double> previousCloseByCode, Map<String, Stock> stockByCode) {
        TossPriceResponse response;
        try {
            response = tossApiClient.getCurrentPrices(String.join(",", chunkCodes));
        } catch (ExternalApiException e) {
            log.warn("해외 관심종목 시세 조회 실패(다음 틱에 재시도): error={}", e.getMessage());
            return List.of();
        }

        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null) {
            return List.of();
        }

        // 종목당 개별 save 대신 청크 전체를 파이프라인 하나로 저장한다
        // (2026-08-19, PriceCacheStore.saveAll 참고) - 브로드캐스트는 종목별로
        // 토픽이 달라 그대로 개별 전송한다.
        List<PriceSnapshot> snapshots = new ArrayList<>();
        List<MarketRankingResponse> chunkRanking = new ArrayList<>();
        for (TossPriceResponse.TossPrice price : prices) {
            PriceSnapshot snapshot = toSnapshot(price, previousCloseByCode);
            if (snapshot == null) {
                continue;
            }
            snapshots.add(snapshot);
            MarketRankingResponse ranked = toRanking(snapshot, stockByCode);
            if (ranked != null) {
                chunkRanking.add(ranked);
            }
        }
        priceCacheStore.saveAll(snapshots);
        for (PriceSnapshot snapshot : snapshots) {
            messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + snapshot.stockCode(), snapshot);
        }
        return chunkRanking;
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

    /**
     * 등락률을 계산할 수 없거나(전일종가 없음) 로컬 stock 테이블에 없는
     * 종목은 랭킹에서 조용히 제외한다 - 예전 {@code MarketRankingService
     * .overseasWatchlistRanking}과 동일한 규칙(시세 자체는 브로드캐스트
     * 되지만 "관심종목만 보기" 랭킹에는 안 뜨는 것도 그대로).
     */
    private MarketRankingResponse toRanking(PriceSnapshot snapshot, Map<String, Stock> stockByCode) {
        if (snapshot.changeRate() == null) {
            return null;
        }
        Stock stock = stockByCode.get(snapshot.stockCode());
        if (stock == null) {
            return null;
        }
        // 스코어는 MarketRankingService.enrichWithScore가 응답 직전에 한 번만
        // 조인한다(DomesticMarketPriceSweepScheduler와 동일한 이유).
        return new MarketRankingResponse(stock.getStockCode(), stock.getDisplayName(), stock.getSector(),
            snapshot.currentPrice(), snapshot.changeRate(), CURRENCY_USD, null, null,
            StockMapper.toLogoUrl(stock), true, null, null, null);
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
