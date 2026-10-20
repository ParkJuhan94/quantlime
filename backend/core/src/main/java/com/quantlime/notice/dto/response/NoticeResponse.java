package com.quantlime.notice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record NoticeResponse(
    Long id,
    String title,
    String content,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime createdAt,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime updatedAt
) {
}
