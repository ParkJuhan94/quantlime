package com.quantlab.stock.cache;

import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.mapper.StockMapper;
import com.quantlab.stock.dto.response.StockDetailResponse;
import com.quantlab.watchlist.service.WatchlistService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 검색모달 "인기 종목"(관심종목 등록자 수 상위) - watchlist 테이블
 * GROUP BY 집계 쿼리를 검색모달을 열 때마다 매번 실행하지 않도록 TTL
 * 캐싱한다(다른 시장 데이터 캐시들과 동일한 패턴, 2026-07-18 피드백 -
 * "폴링하지 말고 캐시 가져오도록"). 관심종목 등록 추이는 분 단위로 잘
 * 안 바뀌니 {@value #TTL_SECONDS}초(5분)면 충분하다. 요청 limit(1~20)마다
 * 캐시를 따로 두지 않고 최대치 하나만 캐싱한 뒤 필요한 만큼만 잘라 쓴다.
 * Stock 엔티티가 아니라 DTO로 변환해 캐싱한다 - TTL이 지날 때까지 JPA
 * 엔티티를 트랜잭션 밖에 들고 있으면 지연 로딩 등에서 문제가 생길 수 있다.
 */
@Component
@RequiredArgsConstructor
public class PopularStocksCache {

    private static final int TTL_SECONDS = 300;
    private static final int MAX_SIZE = 20;

    private final WatchlistService watchlistService;

    private volatile List<StockDetailResponse> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public List<StockDetailResponse> get(int limit) {
        if (isStale()) {
            refresh();
        }
        return cached.subList(0, Math.min(limit, cached.size()));
    }

    private boolean isStale() {
        return cached == null || Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        List<Stock> stocks = watchlistService.getPopularStocks(MAX_SIZE);
        cached = stocks.stream().map(StockMapper::toStockDetailResponse).toList();
        cachedAt = Instant.now();
    }
}
