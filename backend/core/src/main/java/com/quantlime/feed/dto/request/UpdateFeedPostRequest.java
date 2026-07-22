package com.quantlime.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFeedPostRequest(
    @NotBlank(message = "주제는 필수입니다.")
    String category,

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
    String title,

    // 이미지 첨부는 선택 - null이면 이미지 없는 상태로, 기존 URL을 그대로
    // 보내면 유지, 새 URL이면 교체된다(전체 교체 방식, 부분 patch 아님).
    String imageUrl
) {
}
