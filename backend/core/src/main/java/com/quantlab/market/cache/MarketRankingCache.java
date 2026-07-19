package com.quantlab.market.cache;

import com.quantlab.market.dto.response.MarketRankingResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * {@code MarketPriceSweepScheduler}가 매 틱 계산한 전종목 등락률 랭킹의
 * 최신 스냅샷을 들고 있는다. 단일 인스턴스 배치 결과라 Redis 없이
 * 메모리에만 보관한다 - 재시작 시 초기화되지만, 다음 스케줄러 틱(장중
 * 기준 수 초 내)에 다시 채워진다.
 */
@Component
public class MarketRankingCache {

    private volatile List<MarketRankingResponse> latestRanking = List.of();

    public void update(List<MarketRankingResponse> ranking) {
        this.latestRanking = List.copyOf(ranking);
    }

    public List<MarketRankingResponse> getGainers(int limit, Set<String> stockCodes) {
        return filtered(stockCodes)
            .sorted(Comparator.comparingDouble(MarketRankingResponse::changeRate).reversed())
            .limit(limit)
            .toList();
    }

    public List<MarketRankingResponse> getLosers(int limit, Set<String> stockCodes) {
        return filtered(stockCodes)
            .sorted(Comparator.comparingDouble(MarketRankingResponse::changeRate))
            .limit(limit)
            .toList();
    }

    /**
     * stockCodes가 null이면 전종목, 아니면 그 코드들만 - "관심종목만 보기"
     * 토글용. 전종목 스냅샷 안에서 필터링하므로(top-50 등으로 미리 잘려
     * 있지 않음) 관심종목이 상위권 밖에 있어도 정확히 걸러진다.
     */
    private Stream<MarketRankingResponse> filtered(Set<String> stockCodes) {
        Stream<MarketRankingResponse> stream = latestRanking.stream();
        return stockCodes == null ? stream : stream.filter(item -> stockCodes.contains(item.stockCode()));
    }
}
