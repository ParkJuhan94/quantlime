package com.quantlime.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.dto.request.CreateNoticeRequest;
import com.quantlime.notice.dto.response.CreateNoticeResponse;
import com.quantlime.notice.implement.NoticeAppender;
import com.quantlime.notice.implement.NoticeReader;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeReader noticeReader;

    @Mock
    private NoticeAppender noticeAppender;

    @Mock
    private FcmPushService fcmPushService;

    @Mock
    private UserService userService;

    @InjectMocks
    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        Notice saved = Notice.of("점검 안내", "02:00~03:00 점검");
        ReflectionTestUtils.setField(saved, "id", 7L);
        given(noticeAppender.save(any(Notice.class))).willReturn(saved);
    }

    @Test
    @DisplayName("[알림 발송을 요청하면 전체 사용자에게 공지 링크로 보내고 성공을 응답한다]")
    void create_withNotification_sendsToAllUsers() {
        given(userService.getAllUserIds()).willReturn(List.of(1L, 2L));

        CreateNoticeResponse response = noticeService.create(new CreateNoticeRequest("점검 안내", "02:00~03:00 점검", true));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.notificationRequested()).isTrue();
        assertThat(response.notificationSent()).isTrue();
        verify(fcmPushService).sendToUsers(eq(List.of(1L, 2L)), eq(NotificationType.ADMIN_NOTICE), eq("점검 안내"),
            eq("02:00~03:00 점검"), eq("/notices/7"));
    }

    @Test
    @DisplayName("[알림 발송이 실패해도 공지는 등록된 채로 notificationSent=false를 응답한다]")
    void create_notificationFails_keepsNotice() {
        given(userService.getAllUserIds()).willReturn(List.of(1L));
        doThrow(new IllegalStateException("push down")).when(fcmPushService)
            .sendToUsers(any(), any(), any(), any(), any());

        CreateNoticeResponse response = noticeService.create(new CreateNoticeRequest("점검 안내", "02:00~03:00 점검", true));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.notificationRequested()).isTrue();
        assertThat(response.notificationSent()).isFalse();
        verify(noticeAppender).save(any(Notice.class));
    }

    @Test
    @DisplayName("[알림 발송을 요청하지 않으면 푸시 서비스를 호출하지 않는다]")
    void create_withoutNotification_doesNotPush() {
        CreateNoticeResponse response = noticeService.create(new CreateNoticeRequest("점검 안내", "02:00~03:00 점검", false));

        assertThat(response.notificationRequested()).isFalse();
        assertThat(response.notificationSent()).isFalse();
        verify(fcmPushService, never()).sendToUsers(any(), any(), any(), any(), any());
    }
}
