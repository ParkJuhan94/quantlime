package com.quantlime.price.config;

import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link PreviousCloseCache}/{@link WatchlistedStockCodeCache}는 생성자로
 * 시장별 조회 로직/범위를 주입받는 구조라(2026-08-01 국내/해외 통합 - 각
 * 클래스 주석 참고) 컴포넌트 스캔으로 자동 등록될 수 없다 - 국내/해외
 * 인스턴스를 여기서 Bean으로 명시 등록한다({@code
 * MarketDataRefreshTaskExecutorConfig}와 동일한 시장별 Bean 2개 패턴).
 */
@Configuration
public class PriceCacheConfig {

    @Bean
    public PreviousCloseCache domesticPreviousCloseCache(DomesticDailyPriceRepository domesticDailyPriceRepository) {
        return new PreviousCloseCache((stockCodes, today) ->
            domesticDailyPriceRepository.findLatestBeforeDate(stockCodes, today).stream()
                .collect(Collectors.toMap(DomesticDailyPrice::getStockCode, PriceCacheConfig::toDouble)));
    }

    @Bean
    public PreviousCloseCache overseasPreviousCloseCache(OverseasDailyPriceRepository overseasDailyPriceRepository) {
        return new PreviousCloseCache((stockCodes, today) ->
            overseasDailyPriceRepository.findLatestBeforeDate(stockCodes, today).stream()
                .collect(Collectors.toMap(OverseasDailyPrice::getStockCode, OverseasDailyPrice::getClosePrice)));
    }

    @Bean
    public WatchlistedStockCodeCache domesticWatchlistedStockCodeCache(WatchlistRepository watchlistRepository) {
        return new WatchlistedStockCodeCache(watchlistRepository, MarketType.domesticValues());
    }

    @Bean
    public WatchlistedStockCodeCache overseasWatchlistedStockCodeCache(WatchlistRepository watchlistRepository) {
        return new WatchlistedStockCodeCache(watchlistRepository, MarketType.overseasValues());
    }

    private static Double toDouble(DomesticDailyPrice domesticDailyPrice) {
        Long closePrice = domesticDailyPrice.getClosePrice();
        return closePrice == null ? null : closePrice.doubleValue();
    }
}
