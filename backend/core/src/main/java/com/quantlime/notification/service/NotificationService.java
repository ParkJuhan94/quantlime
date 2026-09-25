package com.quantlime.notification.service;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.dto.mapper.NotificationMapper;
import com.quantlime.notification.dto.response.NotificationResponse;
import com.quantlime.notification.exception.NotificationErrorCode;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.user.service.UserService;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public void save(Long userId, NotificationType type, String title, String content, String linkUrl) {
        User user = userService.getById(userId);
        notificationRepository.save(Notification.of(user, type, title, content, linkUrl));
    }

    // 관리자 공지/전체 스코어 랭킹처럼 다수 사용자에게 동시에 보내는
    // 브로드캐스트 전용 - 사용자 수만큼 개별 insert 대신 한 번의
    // saveAll로 묶는다(FcmPushService.sendToUsers).
    @Transactional
    public void saveAll(Collection<Long> userIds, NotificationType type, String title, String content,
                        String linkUrl) {
        List<Notification> notifications = userRepository.findAllById(userIds).stream()
            .map(user -> Notification.of(user, type, title, content, linkUrl))
            .toList();
        notificationRepository.saveAll(notifications);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        Slice<Notification> notifications =
            notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(notifications.map(NotificationMapper::toNotificationResponse));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotFoundException(NotificationErrorCode.NOT_FOUND_NOTIFICATION));
        // 존재 여부와 소유권 미스매치를 같은 예외로 응답해, 다른 사용자의
        // 알림 id가 유효한지 여부를 응답 차이로 추론할 수 없게 한다.
        if (!notification.getUser().getId().equals(userId)) {
            throw new NotFoundException(NotificationErrorCode.NOT_FOUND_NOTIFICATION);
        }
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
