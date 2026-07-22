package com.quantlime.stock.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.stock.exception.StockErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketType {

    KOSPI("코스피", true),
    KOSDAQ("코스닥", true),
    KONEX("코넥스", true),
    // 백테스트 해외 유니버스(KIS 연동, CLAUDE.md 스코어링 백테스트 계획 참고)용.
    // 국내 전용 소스(KIND)를 순회하는 기존 로직(StockMasterSyncService)이
    // 이 값들을 건드리지 않도록 domesticValues()로 명시적으로 걸러낸다.
    NASDAQ("나스닥", false),
    NYSE("뉴욕증권거래소", false);

    private final String label;
    private final boolean domestic;

    public static MarketType of(String label) {
        return Arrays.stream(values())
            .filter(m -> m.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(StockErrorCode.INVALID_MARKET_TYPE));
    }

    public static List<MarketType> domesticValues() {
        return Arrays.stream(values()).filter(MarketType::isDomestic).toList();
    }
}
