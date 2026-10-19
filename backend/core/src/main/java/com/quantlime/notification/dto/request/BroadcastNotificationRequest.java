package com.quantlime.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 길이 한도는 notification 테이블 컬럼(title 100, content 500, link_url 500)과 맞춘다 - 넘으면 INSERT가
// DB 에러(500)로 터지므로 입력 단계에서 400으로 거른다.
public record BroadcastNotificationRequest(
    @Schema(description = "알림 제목(최대 100자)", example = "서비스 점검 안내")
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.") String title,
    @Schema(description = "알림 내용(최대 500자)", example = "오늘 02:00~03:00 점검이 예정되어 있습니다.")
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 500, message = "내용은 500자를 넘을 수 없습니다.") String content,
    @Schema(description = "알림 클릭 시 이동할 사이트 내 경로(선택, /로 시작)", example = "/feed", nullable = true)
    @Size(max = 500, message = "링크는 500자를 넘을 수 없습니다.") String linkUrl
) {
}
