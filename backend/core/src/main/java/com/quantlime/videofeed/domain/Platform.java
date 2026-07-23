package com.quantlime.videofeed.domain;

import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.common.exception.ValidationException;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Platform {

    YOUTUBE("유튜브"),
    TELEGRAM("텔레그램");

    private final String label;

    public static Platform of(String label) {
        return Arrays.stream(values())
            .filter(platform -> platform.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.INVALID_PLATFORM));
    }
}
