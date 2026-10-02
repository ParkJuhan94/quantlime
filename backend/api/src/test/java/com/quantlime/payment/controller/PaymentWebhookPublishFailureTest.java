package com.quantlime.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.event.publish.KafkaPublishException;
import com.quantlime.support.ApiTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

/**
 * 웹훅은 컨트롤러가 200을 돌려주면 Toss 재전송이 멈추므로, 브로커 발행을 확인하지 못했을 때는 반드시
 * 5xx로 응답해 재전송을 유도해야 한다(2026-10-01, Kafka 점검 - 이전에는 발행 실패가 조용히 삼켜진 채
 * 200이 나가 웹훅이 영구 유실됐다). 컨슈머가 필요 없는 경로라 리스너를 켜지 않는다(application-test.yml).
 */
@Tag("integration")
class PaymentWebhookPublishFailureTest extends ApiTestSupport {

    @MockBean
    private KafkaEventSender kafkaEventSender;

    @Test
    @DisplayName("[브로커 발행 확인에 실패하면 웹훅 응답이 5xx다 - Toss가 재전송하게 하려는 것]")
    void webhook_whenBrokerConfirmationFails_respondsWith5xx() throws Exception {
        // given
        willThrow(new KafkaPublishException("payment.webhook.received", new IllegalStateException("broker down")))
            .given(kafkaEventSender).sendAndConfirm(anyString(), anyString(), any(), any(Duration.class));

        // when & then
        mockMvc.perform(post("/api/webhooks/tosspayments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"fail-001\"}}"))
            .andExpect(status().is5xxServerError());
    }
}
