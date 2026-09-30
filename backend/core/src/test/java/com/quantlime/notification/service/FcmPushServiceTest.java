package com.quantlime.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.user.UserFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private FcmTokenService fcmTokenService;

    @Mock
    private NotificationService notificationService;

    private FcmPushService service(boolean firebaseEnabled) {
        return new FcmPushService(firebaseEnabled ? firebaseMessaging : null, fcmTokenService, notificationService);
    }

    private FcmToken token(String value) {
        return FcmToken.of(UserFixture.createUser(), value, "Chrome");
    }

    private List<FcmToken> tokens(int count) {
        List<FcmToken> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tokens.add(token("tok-" + i));
        }
        return tokens;
    }

    private SendResponse ok() {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(true);
        return response;
    }

    private SendResponse failed(MessagingErrorCode code) {
        SendResponse response = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(code);
        given(response.isSuccessful()).willReturn(false);
        given(response.getException()).willReturn(exception);
        return response;
    }

    private BatchResponse batch(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        given(batch.getResponses()).willReturn(List.of(responses));
        return batch;
    }

    @Test
    @DisplayName("[Firebase가 설정되지 않아도(null) 푸시만 건너뛰고 인앱 알림은 저장한다]")
    void sendToUser_firebaseDisabled_stillSavesInApp() {
        // given
        given(fcmTokenService.getTokensForUser(1L)).willReturn(List.of(token("a")));

        // when
        service(false).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        // then
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", "/l");
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    @DisplayName("[푸시가 예외로 실패해도 예외는 전파되지 않고 인앱 알림은 저장된다(인앱이 source of truth)]")
    void sendToUser_pushThrows_stillSavesInApp() throws Exception {
        // given
        given(fcmTokenService.getTokensForUser(1L)).willReturn(List.of(token("a")));
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
            .willThrow(new RuntimeException("fcm down"));

        // when & then
        assertThatCode(() -> service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", null))
            .doesNotThrowAnyException();
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", null);
    }

    @Test
    @DisplayName("[토큰이 없으면 FCM을 호출하지 않는다]")
    void sendToUser_noTokens_skipsPush() {
        given(fcmTokenService.getTokensForUser(1L)).willReturn(List.of());

        service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        verifyNoInteractions(firebaseMessaging);
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", "/l");
    }

    @Test
    @DisplayName("[UNREGISTERED 응답 토큰만 삭제하고, 다른 실패는 토큰을 유지한다]")
    void sendToUser_unregisteredTokenIsDeleted_otherFailuresKept() throws Exception {
        // given
        List<FcmToken> userTokens = List.of(token("good"), token("dead"), token("flaky"));
        // 다른 목을 만드는 헬퍼는 given(...) 인자 안에서 호출하면 중첩 스터빙 오류가 나므로 먼저 만든다
        BatchResponse response =
            batch(ok(), failed(MessagingErrorCode.UNREGISTERED), failed(MessagingErrorCode.INTERNAL));
        given(fcmTokenService.getTokensForUser(1L)).willReturn(userTokens);
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(response);

        // when
        service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        // then
        verify(fcmTokenService).deleteToken("dead");
        verify(fcmTokenService, never()).deleteToken("good");
        verify(fcmTokenService, never()).deleteToken("flaky");
    }

    @Test
    @DisplayName("[sendToUsers: 빈 대상이면 아무것도 하지 않는다]")
    void sendToUsers_empty_noop() {
        service(true).sendToUsers(List.of(), NotificationType.SYSTEM, "t", "c", null);

        verifyNoInteractions(notificationService, fcmTokenService, firebaseMessaging);
    }

    @Test
    @DisplayName("[sendToUsers: 인앱 알림을 일괄 저장하고, 토큰이 500개를 넘으면 500개씩 나눠 발송한다]")
    void sendToUsers_savesAllThenChunksPushesBy500() throws Exception {
        // given: 토큰 1200개 -> 500 + 500 + 200
        List<FcmToken> manyTokens = tokens(1200);
        BatchResponse emptyResponse = batch();
        given(fcmTokenService.getTokensForUsers(List.of(1L, 2L))).willReturn(manyTokens);
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(emptyResponse);

        // when
        service(true).sendToUsers(List.of(1L, 2L), NotificationType.ADMIN_NOTICE, "공지", "내용", "/n");

        // then
        verify(notificationService).saveAll(List.of(1L, 2L), NotificationType.ADMIN_NOTICE, "공지", "내용", "/n");
        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging, org.mockito.Mockito.times(3)).sendEachForMulticast(captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
    }
}
