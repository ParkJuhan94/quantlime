package com.quantlab.price.repository;

import com.quantlab.price.domain.DailyPrice;
import java.util.List;

public interface DailyPriceQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 가장 최근(trade_date 최대) 시세 1건만
     * 골라 반환한다. 실시간 시세 브로드캐스트의 등락률 계산(전일 종가 대비)용 -
     * 장중에는 아직 당일 OHLCV가 수집되지 않았으므로(수집은 16시 배치),
     * "가장 최근 저장된 행"이 곧 전일 종가와 같다.
     */
    List<DailyPrice> findLatestByStockCodesIn(List<String> stockCodes);
}
