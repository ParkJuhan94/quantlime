package com.quantlime.price.service;

import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.dto.mapper.PriceMapper;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.stock.service.StockMasterService;
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
    private final DailyPriceService dailyPriceService;
    private final DailyPriceRepository dailyPriceRepository;
    private final PriceCacheStore priceCacheStore;
    private final PreviousCloseCache previousCloseCache;

    /**
     * 전종목 시세 스윕 스케줄러({@code MarketPriceSweepScheduler})가 관심종목
     * 여부와 무관하게 전종목의 최신 시세를 이미 Redis에 적재하고 있으므로,
     * 이를 먼저 조회해 캐시 히트 시 Toss를 다시 호출하지 않는다.
     *
     * <p>캐시 미스(장 시작 직후라 아직 첫 스윕 전이거나, 장이 닫혀 있는
     * 경우 등)일 때 예전엔 여기서 Toss를 직접 호출했는데, 이 경로가
     * {@code MarketPriceSweepScheduler}의 청크 페이싱(120ms 딜레이)과
     * 전혀 무관하게 동작해 - 특히 프론트가 관심종목/최근 본 종목 여러
     * 개를 {@code useStockPricesQuery}로 5초마다 동시에 폴링하면서
     * 매 폴링마다 N개 종목이 한꺼번에 이 경로를 타 N개의 무페이싱 Toss
     * 요청이 순간적으로 몰리는 구조였다 - 장이 닫혀 있으면 스윕이 아예
     * 안 돌아 캐시가 절대 채워지지 않으므로 이 경로가 100% 확률로 매번
     * 발생했다. 결과적으로 초당 토큰 버킷을 넘겨 429가 반복적으로
     * 발생하는 게 실제 운영에서 확인됨(2026-07-17). Toss를 다시 호출하는
     * 대신 이미 DB에 있는 마지막 확정 종가로 응답하도록 수정 - "장마감에도
     * 최근 종가는 보여야 한다"는 요구는 그대로 만족하면서, Toss 호출은
     * {@code MarketPriceSweepScheduler} 하나로만 유지한다(그 스케줄러
     * 자체 주석의 "유일한 가격 조회원" 설계 의도와 일치시킴).
     */
    @Transactional(readOnly = true)
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        stockMasterService.getStockByCode(stockCode);

        Optional<PriceSnapshot> cached = priceCacheStore.find(stockCode);
        if (cached.isPresent()) {
            return PriceMapper.toCurrentPriceResponse(cached.get());
        }

        return dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(latestClose -> {
                Long previousClose = previousCloseCache.get(List.of(stockCode)).get(stockCode);
                return PriceMapper.toCurrentPriceResponse(latestClose, previousClose);
            })
            .orElseGet(() -> {
                log.warn("현재가 없음: stockCode={}", stockCode);
                return new CurrentPriceResponse(stockCode, null, null, null, null);
            });
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
