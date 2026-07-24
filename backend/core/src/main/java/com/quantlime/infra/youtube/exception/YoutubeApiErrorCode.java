package com.quantlime.infra.youtube.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum YoutubeApiErrorCode implements ErrorCode {

    PLAYLIST_ITEMS_INQUIRY_FAILED("업로드 목록 조회에 실패했습니다.", "YT_000"),
    VIDEOS_INQUIRY_FAILED("영상 상세 조회에 실패했습니다.", "YT_001"),
    QUOTA_EXCEEDED("유튜브 API 일일 쿼터를 초과했습니다.", "YT_002");

    private final String message;
    private final String code;
}
