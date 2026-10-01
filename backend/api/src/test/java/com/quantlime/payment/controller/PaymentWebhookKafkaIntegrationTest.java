package com.quantlime.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.payment.implement.PaymentWebhookDedupStore;
import com.quantlime.support.ApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

/**
 * {@code PaymentWebhookController} → {@code PaymentService.handleWebhook} →
 * 실제 Kafka({@code payment.webhook.received}) → {@code PaymentWebhookConsumer}
 * → {@code processWebhookEvent} 전체 경로를 실제 Redpanda 브로커+실제
 * Redis(둘 다 {@link com.quantlime.support.TestContainerSupport})로
 * 검증한다(2026-09-30, 카프카 다도메인 확장 테스트 보강 세션).
 *
 * <p><b>왜 이 테스트가 필요한가</b>: 순수 Mockito 단위 테스트(예:
 * {@code PaymentServiceTest})는 "메서드 A가 메서드 B를 호출하는가"만
 * 검증하고, 실제 메시지가 브로커를 왕복해 도착하는지(토픽명, 파티션 키,
 * JSON 역직렬화 trusted packages 설정 등)는 전혀 검증하지 못한다. 실제로
 * 이 세션에서 {@code application.yml}의 trusted packages 설정 누락으로
 * 컨슈머가 {@code IllegalArgumentException}으로 메시지를 소비하지 못하는
 * 버그를, 자동화 테스트가 아니라 로컬 curl 라이브 검증 중에야 발견했다 -
 * 이 테스트는 그 사각지대를 회귀 방지하기 위한 것이다. 다른 5개 도메인
 * 컨슈머(videofeed/market/subscription/telegramfeed)에도 같은 패턴을
 * 복제할 수 있는 표본(pilot)으로 작성했다.
 *
 * <p><b>범위 밖</b>: {@code @RetryableTopic}의 실제 backoff(30s→90s→270s)
 * 타이밍이나 DLT 도달 자체는 여기서 검증하지 않는다 - 그 시간을 실제로
 * 기다리면 테스트 하나가 6분 이상 걸려 CI에 부적합하다. {@code @DltHandler}
 * 자체의 동작(예외 미전파, 헤더 폴백)은 {@code PaymentWebhookConsumerTest}
 * (event 모듈, 순수 Mockito 단위 테스트)가 이미 검증한다 - "무엇을
 * 자동화된 단위 테스트로, 무엇을 통합 테스트로 검증할지"의 역할 분담이다.
 */
@Tag("integration")
class PaymentWebhookKafkaIntegrationTest extends ApiTestSupport {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PaymentWebhookDedupStore paymentWebhookDedupStore;

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("[웹훅을 발행하면 실제 브로커를 거쳐 컨슈머가 비동기로 소비해 Redis 멱등 마킹을 실제로 남긴다]")
    void webhook_publishedThroughRealBroker_isConsumedAndMarkedProcessed() throws Exception {
        // given
        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"it-test-001\"}}";
        String dedupKey = "payment:webhook:dedup:" + sha256Hex(payload);

        // when
        mockMvc.perform(post("/api/webhooks/tosspayments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // then - 컨트롤러는 발행만 하고 즉시 200을 반환하므로, 컨슈머가 비동기로
        // 처리를 마칠 시간을 폴링으로 기다린다(고정 Thread.sleep 대신).
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(redisTemplate.hasKey(dedupKey)).isTrue());
    }

    @Test
    @DisplayName("[동일 payload가 컨슈머에서 이미 처리된 뒤에는 같은 해시로 멱등 체크를 다시 호출해도 거부된다 - 실제 재전송 시 중복 처리 방지 확인]")
    void webhook_afterConsumption_dedupStoreRejectsSameHashAgain() throws Exception {
        // given
        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"it-test-002\"}}";
        String hash = sha256Hex(payload);
        String dedupKey = "payment:webhook:dedup:" + hash;

        // when - 최초 발행 + 컨슈머가 실제로 처리(마킹)할 때까지 대기
        mockMvc.perform(post("/api/webhooks/tosspayments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(redisTemplate.hasKey(dedupKey)).isTrue());

        // then - Toss가 같은 웹훅을 재전송했다고 가정하고 동일 해시로 다시 체크하면
        // (컨슈머가 내부적으로 호출하는 것과 동일한 메서드) 이미 처리된 것으로
        // 거부돼야 한다 - 테스트에서 계산한 해시와 컨슈머가 실제로 쓴 키가
        // 일치한다는 것 자체도 함께 증명한다.
        assertThat(paymentWebhookDedupStore.markProcessedIfAbsent(hash)).isFalse();
    }
}
