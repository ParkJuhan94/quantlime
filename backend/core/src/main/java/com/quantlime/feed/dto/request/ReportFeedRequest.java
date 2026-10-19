package com.quantlime.feed.dto.request;

import com.quantlime.feed.domain.FeedReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReportFeedRequest(
    @Schema(description = "신고 사유", example = "SPAM")
    @NotNull(message = "신고 사유는 필수입니다.")
    FeedReportReason reason
) {
}
