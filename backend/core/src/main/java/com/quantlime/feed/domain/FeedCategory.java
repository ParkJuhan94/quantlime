package com.quantlime.feed.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.exception.FeedErrorCode;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum FeedCategory {

    DOMESTIC_STOCK("국내주식토론"),
    US_STOCK("미국주식이야기"),
    CHAT("아무말대잔치"),
    PROFIT_PROOF("수익인증", true);

    private final String label;
    // 수익 인증처럼 증빙 이미지(체결내역 캡처 등)가 없으면 의미 없는 주제는
    // 이미지 첨부를 필수로 요구한다(FeedService.validateImageRequirement 참고).
    private final boolean imageRequired;

    FeedCategory(String label) {
        this(label, false);
    }

    FeedCategory(String label, boolean imageRequired) {
        this.label = label;
        this.imageRequired = imageRequired;
    }

    public static FeedCategory of(String label) {
        return Arrays.stream(values())
            .filter(value -> value.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(FeedErrorCode.INVALID_CATEGORY));
    }
}
