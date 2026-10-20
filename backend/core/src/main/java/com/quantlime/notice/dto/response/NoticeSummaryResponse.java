package com.quantlime.notice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

// 목록용 - 본문 전체 대신 앞부분 미리보기만 내려 목록 응답이 가볍다
public record NoticeSummaryResponse(
    Long id,
    String title,
    String preview,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime createdAt
) {
}
