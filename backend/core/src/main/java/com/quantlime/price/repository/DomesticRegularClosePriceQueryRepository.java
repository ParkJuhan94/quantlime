package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticRegularClosePrice;
import java.time.LocalDate;
import java.util.List;

public interface DomesticRegularClosePriceQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 {@code date} 이전 가장 최근(trade_date
     * 최대) 정규장 종가 1건만 골라 반환한다. {@code
     * DomesticDailyPriceQueryRepository#findLatestBeforeDate}와 완전히 동일한
     * "당일 제외, 최신 이전 거래일" 의도를 이 테이블에도 그대로 적용한다.
     */
    List<DomesticRegularClosePrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date);
}
