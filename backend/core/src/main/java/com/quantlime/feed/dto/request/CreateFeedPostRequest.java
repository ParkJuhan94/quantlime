package com.quantlime.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedPostRequest(
    @NotBlank(message = "주제는 필수입니다.")
    String category,

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
    String title,

    // 이미지 첨부는 선택 - /api/uploads/images로 먼저 업로드해 받은 경로를 그대로 넘긴다.
    String imageUrl
) {
}
