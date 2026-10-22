package com.quantlime.notification.scheduler;

import com.quantlime.notification.implement.NotificationAppender;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 영상/텔레그램 피드와 동일한 보존기간(14일) 정책을 따른다
// (VideoRetentionScheduler/TelegramPostRetentionScheduler 참고). 운영 EC2가
// 00:00~07:40 중지돼 새벽 시각은 영영 못 돈다 - 영상/텔레그램 정리는 기동
// 캐치업(StartupCatchUpRunner)이 보충하지만 알림 정리는 캐치업에 없어
// 21:30으로 옮겼다(구독 갱신 21:00과 겹치지 않게).
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final long RETENTION_DAYS = 14;

    private final NotificationAppender notificationAppender;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Seoul")
    public void deleteOldNotifications() {
        notificationAppender.deleteByCreatedAtBefore(LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
    }
}
