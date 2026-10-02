package com.quantlime.feed.dto.request;

import com.quantlime.feed.domain.FeedReportReason;
import jakarta.validation.constraints.NotNull;

public record ReportFeedRequest(
    @NotNull(message = "신고 사유는 필수입니다.")
    FeedReportReason reason
) {
}
