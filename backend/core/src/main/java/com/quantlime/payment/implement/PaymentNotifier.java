package com.quantlime.payment.implement;

import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 관련 FCM 푸시 발송을 감싸는 구현 레이어(Implementation) -
 * 알림 문구·타입 조합을 여기 한곳에 모아, {@code PaymentService}가
 * {@code FcmPushService}를 직접 호출하며 문구를 반복 작성하지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentNotifier {

    private final FcmPushService fcmPushService;

    public void notifySubscriptionStarted(Long userId, String planName) {
        fcmPushService.sendToUser(userId, NotificationType.PAYMENT_SUCCESS,
            "구독이 시작되었습니다", planName + " 플랜 결제가 완료됐어요.", "/subscribe");
    }

    /** 아직 ACTIVE(프리미엄 유지)인 상태에서, 내일 재시도 예정임을 알린다. */
    public void notifyRenewalRetryScheduled(Long userId) {
        fcmPushService.sendToUser(userId, NotificationType.PAYMENT_FAILED,
            "결제에 실패했어요", "내일 다시 결제를 시도해요. 구독은 유지 중이니 결제수단을 확인해주세요.",
            "/subscribe");
    }

    /** 재시도가 전부 소진돼 PAST_DUE로 전환됐음을 알린다. */
    public void notifyPastDue(Long userId) {
        fcmPushService.sendToUser(userId, NotificationType.PAYMENT_FAILED,
            "결제에 실패했어요", "카드 결제가 계속 실패해 구독이 일시중지됐어요. 결제수단을 확인해주세요.",
            "/subscribe");
    }
}
