package com.quantlime.price.implement;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국내/해외 일봉 조회를 감싸는 구현 레이어(Implementation) - 다른 도메인(market)의
 * 서비스가 price의 Repository를 직접 잡지 않고 이 컴포넌트를 통해서만 거래대금
 * 순위와 최신 저장일을 읽게 한다.
 */
@Component
@RequiredArgsConstructor
public class DailyPriceReader {

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    /** 거래대금 내림차순 국내 종목코드 - 갱신 순서 결정용(전체 순위). */
    public List<String> findDomesticCodesByTradingValueDesc(LocalDate since) {
        return domesticDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since);
    }

    public List<String> findOverseasCodesByTradingValueDesc(LocalDate since) {
        return overseasDailyPriceRepository.findStockCodesOrderedByTradingValueDesc(since);
    }

    /** 거래대금 상위 N개 국내 종목 - 백테스트 유니버스 선정용. */
    public List<DomesticStockTradingValue> findTopDomesticByTradingValue(LocalDate since, int limit) {
        return domesticDailyPriceRepository.findTopByTradingValue(since, limit);
    }

    public List<OverseasStockTradingValue> findTopOverseasByTradingValue(LocalDate since, int limit) {
        return overseasDailyPriceRepository.findTopByTradingValue(since, limit);
    }

    public Optional<LocalDate> findLatestDomesticTradeDate(String stockCode) {
        return domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(DomesticDailyPrice::getTradeDate);
    }

    public Optional<LocalDate> findLatestOverseasTradeDate(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
    }
}
