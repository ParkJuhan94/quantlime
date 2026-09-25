package com.quantlime.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.quantlime.notification.domain.FcmToken;
import com.quantlime.notification.domain.NotificationType;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * FCM 웹 푸시 발송 + 인앱 알림 저장을 함께 담당한다. HandsUp의 FCMService와
 * 달리 **푸시 전송 성공/실패와 무관하게 항상 인앱 알림을 저장한다** - 인앱
 * 알림함이 source of truth이고 푸시는 best-effort 부가 채널이라는 판단
 * (세션 설계 합의). FirebaseMessaging 빈은 FCM_SERVICE_ACCOUNT_PATH
 * 미설정 시 null일 수 있다(FirebaseConfig 참고) - 이 경우 푸시만
 * 건너뛰고 인앱 저장은 그대로 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    // FCM sendEachForMulticast 1회 호출 상한(500 토큰)
    private static final int FCM_BATCH_SIZE = 500;

    // FCM_SERVICE_ACCOUNT_PATH 미설정 시 FirebaseConfig.firebaseMessaging()이
    // null을 반환한다 - @Nullable이 없으면 Spring이 이 생성자 의존성을 필수로
    // 보고 컨텍스트 기동 자체가 실패한다(2026-09-24 로그로 확인).
    @Nullable
    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenService fcmTokenService;
    private final NotificationService notificationService;

    public void sendToUser(Long userId, NotificationType type, String title, String content, String linkUrl) {
        pushToTokens(fcmTokenService.getTokensForUser(userId), title, content, linkUrl);
        notificationService.save(userId, type, title, content, linkUrl);
    }

    public void sendToUsers(Collection<Long> userIds, NotificationType type, String title, String content,
                            String linkUrl) {
        if (userIds.isEmpty()) {
            return;
        }
        notificationService.saveAll(userIds, type, title, content, linkUrl);
        pushToTokens(fcmTokenService.getTokensForUsers(userIds), title, content, linkUrl);
    }

    private void pushToTokens(List<FcmToken> tokens, String title, String content, String linkUrl) {
        if (firebaseMessaging == null || tokens.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
            sendChunk(tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size())), title, content, linkUrl);
        }
    }

    private void sendChunk(List<FcmToken> chunk, String title, String content, String linkUrl) {
        // 데이터 전용(data-only)으로 보낸다 - notification 필드를 실으면
        // Firebase SW가 백그라운드일 때 알림을 자체적으로 한 번 띄운 뒤
        // 우리 onBackgroundMessage도 호출해 알림이 두 번 뜬다(firebase
        // messaging SW의 onPush 구현 참고). 알림 표시는 프론트
        // (firebase-messaging-sw.js)가 한 경로로 전담한다. FCM data 값은
        // null을 허용하지 않아 빈 문자열로 대체한다.
        MulticastMessage message = MulticastMessage.builder()
            .putData("title", title)
            .putData("body", content)
            .putData("linkUrl", linkUrl != null ? linkUrl : "")
            .addAllTokens(chunk.stream().map(FcmToken::getToken).toList())
            .build();
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    handleFailure(chunk.get(i), responses.get(i).getException());
                }
            }
        } catch (Exception e) {
            log.error("FCM 푸시 발송 실패(청크 전체): error={}", e.getMessage(), e);
        }
    }

    private void handleFailure(FcmToken token, FirebaseMessagingException exception) {
        if (exception != null && exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
            fcmTokenService.deleteToken(token.getToken());
            log.info("무효 FCM 토큰 정리: tokenId={}", token.getId());
            return;
        }
        log.warn("FCM 푸시 발송 실패: tokenId={}, error={}",
            token.getId(), exception != null ? exception.getMessage() : "unknown");
    }
}
