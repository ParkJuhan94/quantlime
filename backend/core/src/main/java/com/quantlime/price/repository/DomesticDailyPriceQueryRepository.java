package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import com.quantlime.price.dto.LiquiditySnapshot;
import java.time.LocalDate;
import java.util.List;

public interface DomesticDailyPriceQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 {@code date} 이전 가장 최근(trade_date
     * 최대) 시세 1건만 골라 반환한다. 실시간 시세 브로드캐스트의 등락률
     * 계산(전일 종가 대비)용.
     *
     * <p>당일(date) 자체는 항상 제외한다 - OHLCV 수집이 16시 배치로만
     * 이뤄지던 때는 장중에 당일 행이 존재할 수 없어 "가장 최근 저장된
     * 행"이 곧 전일 종가와 같았지만, 장중에도 최근 며칠치를 조회해 갭을
     * 메우는 캐치업 로직({@code DomesticDailyPriceService.refreshRecent})이
     * 추가된 뒤로는 당일 행이 장중에 먼저 들어올 수 있어 그 전제가
     * 깨졌다 - 명시적으로 date 미만으로 걸러야 한다.
     */
    List<DomesticDailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date);

    /**
     * {@code since} 이후 누적 거래대금(종가×거래량 합) 상위 {@code limit}
     * 종목. 백테스트 국내 유니버스 선정(거래대금 상위 500)용 - 종목별로
     * 보유한 이력 기간이 달라도(신규상장 등) {@code since} 이전 데이터는
     * 자동으로 집계에서 빠진다.
     */
    List<DomesticStockTradingValue> findTopByTradingValue(LocalDate since, int limit);

    /**
     * {@code since} 이후 누적 거래대금(종가×거래량 합) 상위 순으로 정렬된
     * 전체 종목코드 - limit 없이 전부 반환한다.
     * {@link com.quantlime.market.service.MarketDataRefreshService}가
     * 전종목 가격/스코어 갱신 순서를 결정하는 데 쓴다 - 갱신 도중 프로세스가
     * 중단돼도(리소스 부족·재기동 등) 실사용 비중이 큰 종목이 먼저
     * 반영되게 하기 위함(2026-09 감사 세션 - KOSPI가 KOSDAQ보다 항상 뒤늦게
     * 처리돼 몇 주간 스코어가 갱신 안 되던 문제의 재발 방지책).
     */
    List<String> findStockCodesOrderedByTradingValueDesc(LocalDate since);

    /**
     * {@code since} 이후 종목별 일평균 거래대금(종가×거래량)과 거래량 0인
     * 날 수를 집계한다 - 스코어 랭킹의 유동성/거래정지 필터 및 횡단면
     * 정규화 모집단 결정용({@link com.quantlime.price.domain.StockLiquidity}
     * 참고, 2026-09 감사 세션).
     */
    List<LiquiditySnapshot> findLiquiditySnapshot(LocalDate since);
}
