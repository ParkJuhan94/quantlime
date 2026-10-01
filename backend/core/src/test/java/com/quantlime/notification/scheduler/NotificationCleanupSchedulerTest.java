package com.quantlime.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.quantlime.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationCleanupScheduler scheduler;

    @Test
    @DisplayName("[14일보다 오래된 알림을 삭제한다 - 기준 시각이 지금-14일이다]")
    void deleteOldNotifications_usesFourteenDayCutoff() {
        // given
        LocalDateTime before = LocalDateTime.now().minusDays(14);

        // when
        scheduler.deleteOldNotifications();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteByCreatedAtBefore(captor.capture());
        LocalDateTime after = LocalDateTime.now().minusDays(14);
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
