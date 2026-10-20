package com.quantlime.notice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateNoticeResponse(
    @Schema(description = "등록된 공지 ID", example = "1")
    Long id,
    @Schema(description = "알림 발송을 요청했는지", example = "true")
    boolean notificationRequested,
    @Schema(description = "알림 발송이 성공했는지. 요청했는데 false면 공지는 등록됐지만 알림 발송에 실패한 것이다(공지를 다시 등록하지 말고 알림 발송 화면을 쓸 것)", example = "true")
    boolean notificationSent
) {
}
