package com.quantlab.stock.domain;

import com.quantlab.common.exception.ValidationException;
import com.quantlab.stock.exception.StockErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketType {

    KOSPI("코스피"),
    KOSDAQ("코스닥"),
    KONEX("코넥스");

    private final String label;

    public static MarketType of(String label) {
        return Arrays.stream(values())
            .filter(m -> m.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(StockErrorCode.INVALID_MARKET_TYPE));
    }
}
