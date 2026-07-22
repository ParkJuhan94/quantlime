package com.quantlime.price.repository;

import com.quantlime.price.dto.OverseasStockTradingValue;
import java.time.LocalDate;
import java.util.List;

public interface OverseasDailyPriceQueryRepository {

    /**
     * {@code since} 이후 누적 거래대금(종가×거래량 합) 상위 {@code limit}
     * 종목. 국내 {@code DailyPriceQueryRepository.findTopByTradingValue}와
     * 동일한 목적(백테스트 해외 유니버스 선정)이나, 가격이 소수(Double)라
     * 별도 쿼리로 둔다.
     */
    List<OverseasStockTradingValue> findTopByTradingValue(LocalDate since, int limit);
}
