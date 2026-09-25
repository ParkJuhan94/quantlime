package com.quantlime.price.repository;

import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.dto.OverseasStockTradingValue;
import java.time.LocalDate;
import java.util.List;

public interface OverseasDailyPriceQueryRepository {

    /**
     * {@code since} 이후 누적 거래대금(종가×거래량 합) 상위 {@code limit}
     * 종목. 국내 {@code DomesticDailyPriceQueryRepository.findTopByTradingValue}와
     * 동일한 목적(백테스트 해외 유니버스 선정)이나, 가격이 소수(Double)라
     * 별도 쿼리로 둔다.
     */
    List<OverseasStockTradingValue> findTopByTradingValue(LocalDate since, int limit);

    /**
     * {@code since} 이후 누적 거래대금(종가×거래량 합) 상위 순으로 정렬된
     * 전체 종목코드 - limit 없이 전부 반환한다. 국내
     * {@link DomesticDailyPriceQueryRepository#findStockCodesOrderedByTradingValueDesc}와
     * 동일한 목적(전종목 갱신 순서 결정, 부분 실패 시 실사용 비중이 큰
     * 종목 우선 반영).
     */
    List<String> findStockCodesOrderedByTradingValueDesc(LocalDate since);

    /**
     * {@code since} 이후 종목별 일평균 거래대금(종가×거래량)과 거래량 0인
     * 날 수를 집계한다. 국내
     * {@link DomesticDailyPriceQueryRepository#findLiquiditySnapshot}와 동일한
     * 목적(스코어 랭킹 유동성/거래정지 필터, 횡단면 정규화 모집단 결정).
     */
    List<LiquiditySnapshot> findLiquiditySnapshot(LocalDate since);

    /**
     * 주어진 종목 코드들 중 각 종목의 {@code date} 이전 가장 최근
     * (trade_date 최대) 시세 1건만 골라 반환한다. 국내
     * {@code DomesticDailyPriceQueryRepository.findLatestBeforeDate}와 동일한
     * 목적(해외 관심종목 실시간가 등락률 계산용 전일 종가) - 당일(date)
     * 자체는 항상 제외한다.
     */
    List<OverseasDailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date);
}
