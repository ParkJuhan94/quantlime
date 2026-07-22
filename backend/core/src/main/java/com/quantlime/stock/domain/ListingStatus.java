package com.quantlime.stock.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.stock.exception.StockErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ListingStatus {

    LISTED("상장"),
    DELISTED("상장폐지"),
    SUSPENDED("거래정지");

    private final String label;

    public static ListingStatus of(String label) {
        return Arrays.stream(values())
            .filter(s -> s.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(
                StockErrorCode.INVALID_LISTING_STATUS));
    }
}
