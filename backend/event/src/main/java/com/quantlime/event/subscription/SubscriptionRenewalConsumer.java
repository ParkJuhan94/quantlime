package com.quantlime.event.subscription;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code subscription.renewal.due} 토픽을 소비해 구독 자동 갱신 결제를
 * 처리한다(2026-09-24, 카프카 다도메인 확장 Phase 2). videofeed/market과
 * 동일한 non-blocking retry 패턴이지만, 여기서는 {@code
 * PaymentService.chargeRenewal}이 카드 거절(업무적 거절, 4xx)과 일시
 * 장애(5xx/타임아웃)를 구분해 **일시 장애일 때만 예외를 던진다** -
 * 카드 거절은 재시도해도 결과가 같아 애초에 이 리스너까지 예외가
 * 올라오지 않는다(PaymentService 클래스 주석 참고).
 *
 * <p>동시성을 지정하지 않아 기본값 1을 쓴다 - 결제 도메인이라 동시
 * 처리로 얻을 이점보다, 단일 스레드로 순차 처리해 추론을 단순하게
 * 유지하는 쪽을 택했다(멱등성 방어가 있긴 하지만 방어선을 늘릴 이유가 없음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalConsumer {

    private final PaymentService paymentService;
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 30_000, multiplier = 3.0, maxDelay = 270_000))
    @KafkaListener(topics = SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE, groupId = "subscription-renewal-collector")
    public void onRenewalDue(SubscriptionRenewalDueMessage message) {
        paymentService.chargeRenewal(message.subscriptionId());
    }

    @DltHandler
    public void onDlt(SubscriptionRenewalDueMessage message,
        @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessage) {
        log.error("구독 자동 갱신 카프카 재시도 소진(일시 장애 추정) - 기존 DB 재시도(내일/PAST_DUE) 경로로 합류: "
                + "subscriptionId={}, error={}",
            message.subscriptionId(), exceptionMessage);
        paymentService.handleRenewalRetriesExhausted(message.subscriptionId(), exceptionMessage);
        dltNotifier.notify("subscription-renewal", SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE,
            "subscriptionId=" + message.subscriptionId() + " - 카프카 재시도(6.5분) 소진, "
                + "기존 DB 재시도(내일/PAST_DUE) 경로로 처리함. error=" + exceptionMessage);
    }
}
