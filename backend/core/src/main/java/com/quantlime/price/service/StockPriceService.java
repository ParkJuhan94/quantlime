package com.quantlime.price.service;

import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.mapper.PriceMapper;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.stock.domain.Stock;
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

    private static final String KRW = "KRW";
    private static final String USD = "USD";

    private final StockMasterService stockMasterService;
    private final DomesticDailyPriceService domesticDailyPriceService;
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final PriceCacheStore priceCacheStore;

    /**
     * 전종목 시세 스윕 스케줄러({@code DomesticMarketPriceSweepScheduler})가 관심종목
     * 여부와 무관하게 전종목의 최신 시세를 이미 Redis에 적재하고 있으므로,
     * 이를 먼저 조회해 캐시 히트 시 Toss를 다시 호출하지 않는다.
     *
     * <p>캐시 미스(장 시작 직후라 아직 첫 스윕 전이거나, 장이 닫혀 있는
     * 경우 등)일 때 예전엔 여기서 Toss를 직접 호출했는데, 이 경로가
     * {@code DomesticMarketPriceSweepScheduler}의 청크 페이싱(120ms 딜레이)과
     * 전혀 무관하게 동작해 - 특히 프론트가 관심종목/최근 본 종목 여러
     * 개를 {@code useStockPricesQuery}로 5초마다 동시에 폴링하면서
     * 매 폴링마다 N개 종목이 한꺼번에 이 경로를 타 N개의 무페이싱 Toss
     * 요청이 순간적으로 몰리는 구조였다 - 장이 닫혀 있으면 스윕이 아예
     * 안 돌아 캐시가 절대 채워지지 않으므로 이 경로가 100% 확률로 매번
     * 발생했다. 결과적으로 초당 토큰 버킷을 넘겨 429가 반복적으로
     * 발생하는 게 실제 운영에서 확인됨(2026-07-17). Toss를 다시 호출하는
     * 대신 이미 DB에 있는 마지막 확정 종가로 응답하도록 수정 - "장마감에도
     * 최근 종가는 보여야 한다"는 요구는 그대로 만족하면서, Toss 호출은
     * {@code DomesticMarketPriceSweepScheduler} 하나로만 유지한다(그 스케줄러
     * 자체 주석의 "유일한 가격 조회원" 설계 의도와 일치시킴).
     */
    @Transactional(readOnly = true)
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);

        Optional<PriceSnapshot> cached = priceCacheStore.find(stockCode);
        if (cached.isPresent()) {
            String currency = stock.getMarketType().isDomestic() ? KRW : USD;
            return PriceMapper.toCurrentPriceResponse(cached.get(), currency);
        }

        return stock.getMarketType().isDomestic()
            ? domesticFallback(stockCode)
            : overseasFallback(stockCode);
    }

    // 캐시 미스 시 DB의 마지막 확정 종가로 응답 - Toss 재호출 금지 원칙
    // (아래 클래스 주석 참고)은 국내/해외 동일하게 적용한다.
    //
    // 전일종가는 PreviousCloseCache(오늘 기준 "전일" 고정)를 쓰지 않고
    // latestClose.getTradeDate() 기준으로 직접 조회한다 - latestClose가
    // 실제 오늘 거래일 종가일 때만 "오늘 이전 최신 종가"가 올바른 전일종가와
    // 일치한다. 이 폴백은 캐시 미스(장 마감/주말/공휴일, 또는 스윕 첫 틱
    // 이전)일 때 도는데, 이런 경우 latestClose는 대개 오늘이 아닌 지난
    // 거래일 종가라 PreviousCloseCache로 조회하면 "오늘 이전 최신 종가"가
    // latestClose 자기 자신과 같아져 등락률이 항상 0%로 계산되는 버그가
    // 있었다(예: 토요일에 조회하면 전일종가도 금요일 종가가 잡혀 금요일
    // 등락률이 통째로 사라짐) - 실시간 스윕/릴레이 경로는 isMarketOpenNow()
    // 가드가 있어 "오늘=거래일"이 항상 성립하므로 이 문제가 없었다.
    private CurrentPriceResponse domesticFallback(String stockCode) {
        return domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(latestClose -> {
                Double previousClose = domesticDailyPriceRepository
                    .findLatestBeforeDate(List.of(stockCode), latestClose.getTradeDate())
                    .stream()
                    .findFirst()
                    .map(DomesticDailyPrice::getClosePrice)
                    .map(Long::doubleValue)
                    .orElse(null);
                return PriceMapper.toCurrentPriceResponse(latestClose, previousClose);
            })
            .orElseGet(() -> {
                log.warn("현재가 없음: stockCode={}", stockCode);
                return new CurrentPriceResponse(stockCode, null, null, null, null);
            });
    }

    private CurrentPriceResponse overseasFallback(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(latestClose -> {
                Double previousClose = overseasDailyPriceRepository
                    .findLatestBeforeDate(List.of(stockCode), latestClose.getTradeDate())
                    .stream()
                    .findFirst()
                    .map(OverseasDailyPrice::getClosePrice)
                    .orElse(null);
                return PriceMapper.toCurrentPriceResponse(latestClose, previousClose);
            })
            .orElseGet(() -> {
                log.warn("현재가 없음(해외): stockCode={}", stockCode);
                return new CurrentPriceResponse(stockCode, null, null, null, null);
            });
    }

    // 시장 구분 없이 항상 domestic_daily_price만 조회하던 버그가 있었다 -
    // 해외 종목은 이 테이블에 행이 없어 차트가 항상 빈 배열로 응답됐다.
    @Transactional(readOnly = true)
    public List<DailyChartResponse> getChart(String stockCode, int days) {
        Stock stock = stockMasterService.getStockByCode(stockCode);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        if (!stock.getMarketType().isDomestic()) {
            return overseasDailyPriceRepository
                .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end).stream()
                .map(PriceMapper::toDailyChartResponse)
                .toList();
        }
        return domesticDailyPriceService.getDailyPrices(stockCode, start, end).stream()
            .map(PriceMapper::toDailyChartResponse)
            .toList();
    }
}
