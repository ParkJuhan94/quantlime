package com.quantlime.watchlist.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WatchlistErrorCode implements ErrorCode {

    ALREADY_EXISTS_WATCHLIST("이미 관심 종목으로 등록되어 있습니다.", "WL_000"),
    NOT_FOUND_WATCHLIST("관심 종목을 찾을 수 없습니다.", "WL_001"),
    NOT_FOUND_WATCHLIST_GROUP("관심 그룹을 찾을 수 없습니다.", "WL_002");

    private final String message;
    private final String code;
}
