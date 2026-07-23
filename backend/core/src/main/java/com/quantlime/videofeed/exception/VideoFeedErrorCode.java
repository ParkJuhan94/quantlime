package com.quantlime.videofeed.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VideoFeedErrorCode implements ErrorCode {

    NOT_FOUND_CHANNEL("해당 채널을 찾을 수 없습니다.", "VF_000"),
    INVALID_PLATFORM("올바른 플랫폼을 입력해주세요.", "VF_001"),
    INVALID_VIDEO_STATUS("올바른 영상 상태를 입력해주세요.", "VF_002");

    private final String message;
    private final String code;
}
