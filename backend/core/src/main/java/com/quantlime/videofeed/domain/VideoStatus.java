package com.quantlime.videofeed.domain;

import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.common.exception.ValidationException;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전이: DISCOVERED -> FILTERED_OUT
 *              -> PENDING_REVIEW (velocity 판정 유예, 6h 후 재평가)
 *              -> SELECTED -> TRANSCRIBED -> SUMMARIZED
 *                          -> FAILED (retryCount++)
 */
@Getter
@RequiredArgsConstructor
public enum VideoStatus {

    DISCOVERED("발견됨"),
    FILTERED_OUT("필터링됨"),
    PENDING_REVIEW("판정 유예"),
    SELECTED("선정됨"),
    TRANSCRIBED("자막 추출됨"),
    SUMMARIZED("요약 완료"),
    FAILED("실패");

    private final String label;

    public static VideoStatus of(String label) {
        return Arrays.stream(values())
            .filter(status -> status.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(VideoFeedErrorCode.INVALID_VIDEO_STATUS));
    }
}
