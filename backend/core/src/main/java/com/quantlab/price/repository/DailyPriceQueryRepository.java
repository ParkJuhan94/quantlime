package com.quantlab.price.repository;

import com.quantlab.price.domain.DailyPrice;
import java.time.LocalDate;
import java.util.List;

public interface DailyPriceQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 {@code date} 이전 가장 최근(trade_date
     * 최대) 시세 1건만 골라 반환한다. 실시간 시세 브로드캐스트의 등락률
     * 계산(전일 종가 대비)용.
     *
     * <p>당일(date) 자체는 항상 제외한다 - OHLCV 수집이 16시 배치로만
     * 이뤄지던 때는 장중에 당일 행이 존재할 수 없어 "가장 최근 저장된
     * 행"이 곧 전일 종가와 같았지만, 장중에도 최근 며칠치를 조회해 갭을
     * 메우는 캐치업 로직({@code DailyPriceService.collectDailyPrice})이
     * 추가된 뒤로는 당일 행이 장중에 먼저 들어올 수 있어 그 전제가
     * 깨졌다 - 명시적으로 date 미만으로 걸러야 한다.
     */
    List<DailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date);
}
