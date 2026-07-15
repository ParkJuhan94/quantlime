package com.quantlab.price.service;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.cache.PriceCacheStore;
import com.quantlab.price.dto.mapper.PriceMapper;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import com.quantlab.price.dto.response.PriceSnapshot;
import com.quantlab.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StockMasterService stockMasterService;
    private final TossApiClient tossApiClient;
    private final DailyPriceService dailyPriceService;
    private final PriceCacheStore priceCacheStore;

    /**
     * 전종목 시세 스윕 스케줄러({@code MarketPriceSweepScheduler})가 관심종목
     * 여부와 무관하게 전종목의 최신 시세를 이미 Redis에 적재하고 있으므로,
     * 이를 먼저 조회해 캐시 히트 시 Toss를 다시 호출하지 않는다. 캐시
     * 미스(장 시작 직후라 아직 첫 스윕 전이거나, 장이 닫혀 있는 경우 등)일
     * 때만 기존처럼 Toss를 직접 호출한다.
     */
    @Transactional(readOnly = true)
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        stockMasterService.getStockByCode(stockCode);

        Optional<PriceSnapshot> cached = priceCacheStore.find(stockCode);
        if (cached.isPresent()) {
            return PriceMapper.toCurrentPriceResponse(cached.get());
        }

        TossPriceResponse response = tossApiClient.getCurrentPrices(stockCode);
        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null || prices.isEmpty()) {
            log.warn("현재가 없음: stockCode={}", stockCode);
            return new CurrentPriceResponse(stockCode, null, null, null);
        }

        return PriceMapper.toCurrentPriceResponse(stockCode, prices.get(0));
    }

    @Transactional(readOnly = true)
    public List<DailyChartResponse> getChart(String stockCode, int days) {
        stockMasterService.getStockByCode(stockCode);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        return dailyPriceService.getDailyPrices(stockCode, start, end).stream()
            .map(PriceMapper::toDailyChartResponse)
            .toList();
    }
}
