package com.quantlime.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record BroadcastNotificationRequest(
    @Schema(description = "알림 제목", example = "서비스 점검 안내")
    @NotBlank(message = "제목은 필수입니다.") String title,
    @Schema(description = "알림 내용", example = "오늘 02:00~03:00 점검이 예정되어 있습니다.")
    @NotBlank(message = "내용은 필수입니다.") String content,
    @Schema(description = "알림 클릭 시 이동할 링크(선택)", example = "/notices/1", nullable = true)
    String linkUrl
) {
}
