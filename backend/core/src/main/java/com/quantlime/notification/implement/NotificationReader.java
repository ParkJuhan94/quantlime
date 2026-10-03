package com.quantlime.notification.implement;

import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.repository.NotificationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/** 알림 조회를 감싸는 구현 레이어. 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class NotificationReader {

    private final NotificationRepository notificationRepository;

    public Slice<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }

    public long countByUser_IdAndIsReadFalse(Long userId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(userId);
    }

    public Optional<Notification> findById(Long notificationId) {
        return notificationRepository.findById(notificationId);
    }
}
