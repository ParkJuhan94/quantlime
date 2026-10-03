package com.quantlime.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.dto.response.NotificationResponse;
import com.quantlime.notification.implement.NotificationAppender;
import com.quantlime.notification.implement.NotificationReader;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.implement.UserReader;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
            new NotificationReader(notificationRepository), new NotificationAppender(notificationRepository),
            new UserReader(userRepository), userService);
    }

    @Captor
    private ArgumentCaptor<List<Notification>> notificationsCaptor;

    private User user(long id) {
        User user = UserFixture.createUser();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Notification notificationOf(User user) {
        return Notification.of(user, NotificationType.SYSTEM, "제목", "내용", "/link");
    }

    @Test
    @DisplayName("[save는 사용자를 조회해 알림 1건을 저장한다]")
    void save_persistsNotificationForUser() {
        // given
        User user = user(1L);
        given(userService.getById(1L)).willReturn(user);

        // when
        service.save(1L, NotificationType.ADMIN_NOTICE, "제목", "내용", "/x");

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ADMIN_NOTICE);
        assertThat(captor.getValue().getTitle()).isEqualTo("제목");
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    @DisplayName("[saveAll은 사용자 수만큼 알림을 만들어 saveAll 한 번으로 저장한다]")
    void saveAll_batchesIntoSingleSaveAll() {
        // given
        given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(user(1L), user(2L)));

        // when
        service.saveAll(List.of(1L, 2L), NotificationType.ADMIN_NOTICE, "공지", "내용", null);

        // then
        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        assertThat(notificationsCaptor.getValue()).hasSize(2)
            .allSatisfy(n -> assertThat(n.getTitle()).isEqualTo("공지"));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("[목록 조회는 Slice를 PageResponse로 변환한다]")
    void getNotifications_mapsSliceToPageResponse() {
        // given
        PageRequest pageable = PageRequest.of(0, 2);
        given(notificationRepository.findByUser_IdOrderByCreatedAtDesc(1L, pageable))
            .willReturn(new SliceImpl<>(List.of(notificationOf(user(1L)), notificationOf(user(1L))), pageable, true));

        // when
        PageResponse<NotificationResponse> response = service.getNotifications(1L, pageable);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).title()).isEqualTo("제목");
        assertThat(response.content().get(0).type()).isNotBlank();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("[안읽은 개수는 저장소 값을 그대로 반환한다]")
    void countUnread_delegates() {
        given(notificationRepository.countByUser_IdAndIsReadFalse(1L)).willReturn(3L);

        assertThat(service.countUnread(1L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("[본인 알림이면 읽음 처리된다]")
    void markAsRead_owner_marksRead() {
        // given
        Notification notification = notificationOf(user(1L));
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        // when
        service.markAsRead(10L, 1L);

        // then
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("[다른 사용자의 알림이면 존재하지 않는 것과 같은 NotFoundException - 읽음 처리도 안 된다]")
    void markAsRead_otherUsersNotification_throwsNotFoundAndKeepsUnread() {
        // given
        Notification notification = notificationOf(user(2L));
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> service.markAsRead(10L, 1L)).isInstanceOf(NotFoundException.class);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("[없는 알림 id면 NotFoundException(소유권 미스매치와 동일 예외)]")
    void markAsRead_missing_throwsNotFound() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[전체 읽음 처리는 저장소의 벌크 업데이트에 위임한다]")
    void markAllAsRead_delegates() {
        service.markAllAsRead(1L);

        verify(notificationRepository).markAllAsRead(1L);
    }
}
