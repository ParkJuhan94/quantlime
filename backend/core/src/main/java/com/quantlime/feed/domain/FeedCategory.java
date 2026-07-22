package com.quantlime.feed.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.exception.FeedErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedCategory {

    DOMESTIC_STOCK("국내주식토론"),
    US_STOCK("미국주식이야기"),
    CHAT("아무말대잔치");

    private final String label;

    public static FeedCategory of(String label) {
        return Arrays.stream(values())
            .filter(value -> value.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(FeedErrorCode.INVALID_CATEGORY));
    }
}
