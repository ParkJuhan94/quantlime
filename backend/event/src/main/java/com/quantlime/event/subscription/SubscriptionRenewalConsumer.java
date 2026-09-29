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

    // exceptionMessage 헤더는 반드시 required=false여야 한다(2026-09-30
    // 실제 사고로 발견) - 이 핸들러 자신이 예외를 던지면(예: 헤더 파싱 실패)
    // 그 실패가 "이 DLT 토픽 소비 중 발생한 새 실패"로 취급돼 같은 DLT
    // 토픽에 다시 발행된다(다른 목적지가 없으므로). 헤더를 필수로 요구하면
    // "헤더가 없다"는 이유로 던진 예외 자체가 헤더 없는 메시지를 다시
    // 만들어내 무한 재발행 루프가 된다 - 로컬 실측으로 초당 100개 이상
    // 증식하는 걸 확인했다. required=false로 두면 헤더가 없어도 메서드
    // 자체는 정상 완료돼 이 루프가 원천 차단된다.
    // try/catch 방어(2026-09-30 테스트 보강 세션에서 발견) - 헤더 fallback만
    // 적용돼 있고 이 메서드 자체가 실패할 경우(예: handleRenewalRetriesExhausted가
    // 구독 조회/DB 제약 위반으로 예외를 던지는 경우)의 방어가 나머지 5개
    // @DltHandler와 달리 빠져 있었다 - 다른 핸들러와 동일한 불변식(DltHandler는
    // 절대 예외를 던지지 않는다)을 여기도 적용한다.
    @DltHandler
    public void onDlt(SubscriptionRenewalDueMessage message,
        @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        try {
            String reason = exceptionMessage != null ? exceptionMessage : "사유 미상(DLT 예외 헤더 없음)";
            log.error("구독 자동 갱신 카프카 재시도 소진(일시 장애 추정) - 기존 DB 재시도(내일/PAST_DUE) 경로로 합류: "
                    + "subscriptionId={}, error={}",
                message.subscriptionId(), reason);
            paymentService.handleRenewalRetriesExhausted(message.subscriptionId(), reason);
            dltNotifier.notify("subscription-renewal", SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE,
                "subscriptionId=" + message.subscriptionId() + " - 카프카 재시도(6.5분) 소진, "
                    + "기존 DB 재시도(내일/PAST_DUE) 경로로 처리함. error=" + reason);
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): subscriptionId={}",
                message.subscriptionId(), e);
        }
    }
}
