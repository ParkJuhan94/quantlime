package com.quantlime.notification.dto.mapper;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.dto.response.NotificationResponse;
import com.quantlime.notification.dto.response.NotificationStockResponse;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class NotificationMapper {

    private static List<NotificationStockResponse> toStockResponses(Notification notification) {
        if (notification.getStocks() == null) {
            return List.of();
        }
        return notification.getStocks().stream()
            .map(stock -> new NotificationStockResponse(stock.code(), stock.name()))
            .toList();
    }

    public static NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType().name(),
            notification.getTitle(),
            notification.getContent(),
            notification.getLinkUrl(),
            notification.isRead(),
            notification.getCreatedAt(),
            toStockResponses(notification)
        );
    }
}
