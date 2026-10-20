package com.quantlime.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public record NotificationResponse(
    Long id,
    String type,
    String title,
    String content,
    String linkUrl,
    boolean isRead,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime createdAt,
    // 랭킹·사분면 알림의 종목 목록 - 없으면 빈 배열(화면이 null 분기를 하지 않게)
    List<NotificationStockResponse> stocks
) {
}
