package com.quantlime.notification.dto.mapper;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.dto.response.NotificationResponse;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class NotificationMapper {

    public static NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType().name(),
            notification.getTitle(),
            notification.getContent(),
            notification.getLinkUrl(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }
}
