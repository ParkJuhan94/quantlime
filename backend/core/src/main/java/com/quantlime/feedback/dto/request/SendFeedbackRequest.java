package com.quantlime.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendFeedbackRequest(
    @NotBlank(message = "카테고리는 필수입니다.")
    @Pattern(regexp = "BUG|FEATURE|OTHER", message = "category는 BUG, FEATURE, OTHER 중 하나여야 합니다.")
    String category,

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
    String message,

    String pageUrl,

    // 이미지 첨부는 선택 - /api/uploads/images로 먼저 업로드해 받은 경로를 그대로 넘긴다.
    String imageUrl
) {
}
