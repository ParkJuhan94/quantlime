package com.quantlime.notice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 수정은 알림을 다시 보내지 않는다(이미 나간 알림은 같은 /notices/{id}로 최신 내용을 보게 된다).
public record UpdateNoticeRequest(
    @Schema(description = "공지 제목(최대 200자)", example = "서비스 점검 안내(시간 변경)")
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.") String title,
    @Schema(description = "공지 본문(일반 텍스트, 줄바꿈 유지, 최대 5000자)", example = "점검 시간이 03:00~04:00으로 변경됐습니다.")
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 5000, message = "내용은 5000자를 넘을 수 없습니다.") String content
) {
}
