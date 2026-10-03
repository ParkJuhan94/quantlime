package com.quantlime.notification.implement;

import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 알림 저장·갱신·삭제를 감싸는 구현 레이어. 파생 delete/@Modifying 쿼리라 호출하는 서비스·스케줄러가 @Transactional 경계를 소유한다. */
@Component
@RequiredArgsConstructor
public class NotificationAppender {

    private final NotificationRepository notificationRepository;

    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    public void saveAll(List<Notification> notifications) {
        notificationRepository.saveAll(notifications);
    }

    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    public void deleteByCreatedAtBefore(LocalDateTime dateTime) {
        notificationRepository.deleteByCreatedAtBefore(dateTime);
    }
}
