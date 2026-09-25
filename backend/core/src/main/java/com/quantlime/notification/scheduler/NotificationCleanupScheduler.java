package com.quantlime.notification.scheduler;

import com.quantlime.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 영상/텔레그램 피드와 동일한 보존기간(14일) 정책을 따른다
// (VideoRetentionScheduler/TelegramPostRetentionScheduler 참고). 같은
// 새벽 3시대에 몰린 다른 정리 스케줄러(VideoRetentionScheduler 정각
// 3시)와 겹치지 않게 3시 20분에 실행한다.
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final long RETENTION_DAYS = 14;

    private final NotificationRepository notificationRepository;

    @Transactional
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
    public void deleteOldNotifications() {
        notificationRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
    }
}
