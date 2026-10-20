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
import com.quantlime.notification.domain.NotificationStock;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.user.UserFixture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private FcmPushService service(boolean firebaseEnabled) {
        return new FcmPushService(firebaseEnabled ? firebaseMessaging : null, fcmTokenService, notificationService,
            meterRegistry);
    }

    private double count(String outcome) {
        return meterRegistry.counter(FcmPushService.METRIC_SENDS, "outcome", outcome).count();
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
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", "/l", List.of());
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
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", null, List.of());
    }

    @Test
    @DisplayName("[토큰이 없으면 FCM을 호출하지 않는다]")
    void sendToUser_noTokens_skipsPush() {
        given(fcmTokenService.getTokensForUser(1L)).willReturn(List.of());

        service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        verifyNoInteractions(firebaseMessaging);
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", "/l", List.of());
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
        verify(notificationService).saveAll(List.of(1L, 2L), NotificationType.ADMIN_NOTICE, "공지", "내용", "/n", List.of());
        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging, org.mockito.Mockito.times(3)).sendEachForMulticast(captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
    }

    @Test
    @DisplayName("[토큰 조회가 실패해도 인앱 알림은 먼저 저장돼 있다(저장 후 푸시 순서)]")
    void sendToUser_tokenLookupFails_inAppAlreadySaved() {
        // given
        given(fcmTokenService.getTokensForUser(1L)).willThrow(new IllegalStateException("db down"));

        // when
        assertThatCode(() -> service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", null))
            .isInstanceOf(IllegalStateException.class);

        // then
        verify(notificationService).save(1L, NotificationType.SYSTEM, "t", "c", null, List.of());
    }

    @Test
    @DisplayName("[발송 결과를 success/unregistered/failure 카운터로 센다 - 만료 토큰 정리는 실패로 세지 않는다]")
    void sendToUser_countsOutcomes() throws Exception {
        // given
        List<FcmToken> userTokens = List.of(token("good"), token("dead"), token("flaky"));
        BatchResponse response =
            batch(ok(), failed(MessagingErrorCode.UNREGISTERED), failed(MessagingErrorCode.INTERNAL));
        given(fcmTokenService.getTokensForUser(1L)).willReturn(userTokens);
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(response);

        // when
        service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        // then
        assertThat(count("success")).isEqualTo(1);
        assertThat(count("unregistered")).isEqualTo(1);
        assertThat(count("failure")).isEqualTo(1);
        assertThat(count("batch_error")).isZero();
        assertThat(count("skipped")).isZero();
    }

    @Test
    @DisplayName("[Firebase가 설정되지 않으면 건너뛴 토큰 수만큼 skipped로 센다]")
    void sendToUser_firebaseDisabled_countsSkipped() {
        given(fcmTokenService.getTokensForUser(1L)).willReturn(tokens(3));

        service(false).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        assertThat(count("skipped")).isEqualTo(3);
    }

    @Test
    @DisplayName("[청크 호출 자체가 예외로 실패하면 그 청크의 토큰 수만큼 batch_error로 센다]")
    void sendToUser_chunkThrows_countsBatchError() throws Exception {
        given(fcmTokenService.getTokensForUser(1L)).willReturn(tokens(4));
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
            .willThrow(new RuntimeException("fcm down"));

        service(true).sendToUser(1L, NotificationType.SYSTEM, "t", "c", "/l");

        assertThat(count("batch_error")).isEqualTo(4);
    }

    @Test
    @DisplayName("[카운터는 발송 전에도 0으로 노출된다(알림 규칙이 시리즈 부재로 비지 않게)]")
    void counters_preRegisteredAtZero() {
        service(true);

        assertThat(meterRegistry.find(FcmPushService.METRIC_SENDS).counters()).hasSize(5);
    }

    @Test
    @DisplayName("[종목 목록은 인앱 알림 저장으로만 전달되고 푸시 페이로드에는 실리지 않는다]")
    void sendToUser_withStocks_passesStocksToInAppSave() throws Exception {
        // given
        List<NotificationStock> stocks = List.of(new NotificationStock("005930", "삼성전자"));
        BatchResponse response = batch(ok());
        given(fcmTokenService.getTokensForUser(1L)).willReturn(List.of(token("a")));
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(response);

        // when
        service(true).sendToUser(1L, NotificationType.SCORE_RANKING_GLOBAL, "t", "c", "/", stocks);

        // then
        verify(notificationService).save(1L, NotificationType.SCORE_RANKING_GLOBAL, "t", "c", "/", stocks);
    }
}
