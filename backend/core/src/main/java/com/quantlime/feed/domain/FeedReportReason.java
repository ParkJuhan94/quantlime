package com.quantlime.feed.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedReportReason {

    ADVERTISEMENT("광고/홍보"),
    ABUSE("욕설/비방"),
    SPAM("도배/스팸"),
    FRAUD("허위 인증/사기"),
    OTHER("기타");

    private final String label;
}
