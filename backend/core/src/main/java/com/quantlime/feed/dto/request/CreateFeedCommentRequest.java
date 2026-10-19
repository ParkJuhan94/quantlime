package com.quantlime.feed.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedCommentRequest(
    @Schema(description = "댓글 내용(최대 500자)", example = "좋은 분석 감사합니다.")
    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 500, message = "댓글은 500자를 넘을 수 없습니다.")
    String content
) {
}
