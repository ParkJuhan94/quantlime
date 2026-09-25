package com.quantlime.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String type,
    String title,
    String content,
    String linkUrl,
    boolean isRead,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime createdAt
) {
}
