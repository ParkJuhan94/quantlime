package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.HotSectorResponse;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.implement.StockLiquidityReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * "지금 뜨는 산업" - 국내 전종목 실시간 스냅샷({@link MarketRankingCache})을 섹터로 묶어
 * 거래대금 가중 평균 등락률로 정렬한다(2026-10-01 결정).
 *
 * <p>단순 평균은 소형 잡주 한두 개의 급등락이 섹터 순위를 왜곡하므로(스코어 랭킹의 "잡주
 * 상위 독식 감사"와 같은 문제의식), 가중치로 최근 20거래일 일평균 거래대금
 * ({@link StockLiquidity#getAvgTradingValue20d()})을 쓰고 유동성 필터를 통과하지 못한
 * 종목(거래정지·초저유동성)은 집계에서 뺀다. 종목 수가 {@value #MIN_STOCKS_PER_SECTOR}개 미만인
 * 섹터는 한두 종목이 곧 섹터가 되므로 제외한다.
 *
 * <p>스냅샷은 장중에만 채워지므로({@code DomesticMarketPriceSweepScheduler}) 장 마감 후에는
 * 빈 목록이다 - 실시간 랭킹과 동일한 "장중에만 갱신" 규칙.
 */
@Service
@RequiredArgsConstructor
public class HotSectorService {

    static final int MIN_STOCKS_PER_SECTOR = 3;
    private static final int LEADER_COUNT = 2;

    // 필드명이 MarketRankingCacheConfig의 @Bean 메서드명과 일치해야 한다(MarketRankingService와 동일 관례).
    private final MarketRankingCache domesticMarketRankingCache;
    private final StockLiquidityReader stockLiquidityReader;

    public List<HotSectorResponse> getHotSectors(int limit) {
        List<MarketRankingResponse> snapshot = domesticMarketRankingCache.getAll();
        if (snapshot.isEmpty()) {
            return List.of();
        }

        Map<String, Double> weightByCode = liquidWeights(snapshot);
        Map<String, List<Member>> membersBySector = new HashMap<>();
        for (MarketRankingResponse item : snapshot) {
            Double weight = weightByCode.get(item.stockCode());
            if (weight == null || !StringUtils.hasText(item.sector()) || item.changeRate() == null) {
                continue;
            }
            membersBySector.computeIfAbsent(item.sector(), key -> new ArrayList<>()).add(new Member(item, weight));
        }

        return membersBySector.entrySet().stream()
            .filter(entry -> entry.getValue().size() >= MIN_STOCKS_PER_SECTOR)
            .map(entry -> toResponse(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingDouble(HotSectorResponse::changeRate).reversed())
            .limit(limit)
            .toList();
    }

    private Map<String, Double> liquidWeights(List<MarketRankingResponse> snapshot) {
        List<String> codes = snapshot.stream().map(MarketRankingResponse::stockCode).toList();
        return stockLiquidityReader.findAllByStockCodes(codes).stream()
            .filter(StockLiquidity::isLiquid)
            .filter(liquidity -> liquidity.getAvgTradingValue20d() != null && liquidity.getAvgTradingValue20d() > 0)
            .collect(Collectors.toMap(StockLiquidity::getStockCode, StockLiquidity::getAvgTradingValue20d,
                (a, b) -> a));
    }

    private HotSectorResponse toResponse(String sector, List<Member> members) {
        double totalWeight = members.stream().mapToDouble(Member::weight).sum();
        double weighted = members.stream().mapToDouble(m -> m.item().changeRate() * m.weight()).sum() / totalWeight;
        List<HotSectorResponse.Leader> leaders = members.stream()
            .sorted(Comparator.comparingDouble(Member::weight).reversed())
            .limit(LEADER_COUNT)
            .map(m -> new HotSectorResponse.Leader(m.item().stockCode(), m.item().stockName(), m.item().changeRate()))
            .toList();
        return new HotSectorResponse(sector, weighted, members.size(), leaders);
    }

    private record Member(MarketRankingResponse item, double weight) {
    }
}
