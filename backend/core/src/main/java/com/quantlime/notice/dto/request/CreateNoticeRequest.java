package com.quantlime.notice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoticeRequest(
    @Schema(description = "공지 제목(최대 200자)", example = "서비스 점검 안내")
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.") String title,
    @Schema(description = "공지 본문(일반 텍스트, 줄바꿈 유지, 최대 5000자)", example = "10월 21일 02:00~03:00 점검이 예정되어 있습니다.")
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 5000, message = "내용은 5000자를 넘을 수 없습니다.") String content,
    @Schema(description = "true면 등록과 함께 전체 사용자에게 알림(링크 /notices/{id})을 보낸다", example = "false")
    boolean sendNotification
) {
}
