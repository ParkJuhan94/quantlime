package com.quantlime.feedback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendFeedbackRequest(
    @Schema(description = "피드백 종류", allowableValues = {"BUG", "FEATURE", "OTHER"}, example = "BUG")
    @NotBlank(message = "카테고리는 필수입니다.")
    @Pattern(regexp = "BUG|FEATURE|OTHER", message = "category는 BUG, FEATURE, OTHER 중 하나여야 합니다.")
    String category,

    @Schema(description = "피드백 내용(최대 2000자)", example = "관심종목 정렬이 새로고침하면 초기화됩니다.")
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
    String message,

    @Schema(description = "피드백을 보낸 화면 URL(선택)", example = "https://quantlime.example/watchlist", nullable = true)
    String pageUrl,

    // 이미지 첨부는 선택 - /api/uploads/images로 먼저 업로드해 받은 경로를 그대로 넘긴다.
    @Schema(description = "첨부 이미지 경로(선택). /api/uploads/images로 먼저 업로드해 받은 경로를 그대로 넘긴다", example = "/uploads/images/3f2a1b.png", nullable = true)
    String imageUrl
) {
}
