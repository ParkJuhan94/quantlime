package com.quantlime.event.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.event.market.MarketEventPublisher;
import com.quantlime.event.market.PriceRefreshRequestedMessage;
import com.quantlime.event.payment.PaymentEventPublisher;
import com.quantlime.event.payment.PaymentWebhookReceivedMessage;
import com.quantlime.event.subscription.SubscriptionEventPublisher;
import com.quantlime.event.subscription.SubscriptionRenewalDueMessage;
import com.quantlime.event.telegramfeed.TelegramDigestGenerationRequestedMessage;
import com.quantlime.event.telegramfeed.TelegramFeedEventPublisher;
import com.quantlime.event.videofeed.VideoFeedEventPublisher;
import com.quantlime.event.videofeed.VideoSelectedMessage;
import com.quantlime.event.videofeed.VideoTranscribedMessage;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import com.quantlime.payment.event.PaymentWebhookReceivedEvent;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.subscription.event.SubscriptionRenewalDueEvent;
import com.quantlime.telegramfeed.event.TelegramDigestGenerationRequestedEvent;
import com.quantlime.videofeed.event.VideoSelectedEvent;
import com.quantlime.videofeed.event.VideoTranscribedEvent;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 퍼블리셔 5종의 "도메인 이벤트 → 토픽/파티션 키/메시지" 매핑. 토픽명이나 키가 조용히 바뀌는 걸 막는다. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class EventPublishersTest {

    @Mock
    private KafkaEventSender sender;

    @Test
    @DisplayName("[영상 선정/자막 완료 이벤트는 videoId를 키로 각자의 토픽에 발행된다]")
    void videoFeedPublisher_mapsTopicsAndKeys() {
        // given
        VideoFeedEventPublisher publisher = new VideoFeedEventPublisher(sender);

        // when
        publisher.onVideoSelected(new VideoSelectedEvent(7L));
        publisher.onVideoTranscribed(new VideoTranscribedEvent(8L));

        // then
        ArgumentCaptor<Object> selected = ArgumentCaptor.forClass(Object.class);
        verify(sender).send(eq("video.selected"), eq("7"), selected.capture());
        assertThat(((VideoSelectedMessage) selected.getValue()).videoId()).isEqualTo(7L);

        ArgumentCaptor<Object> transcribed = ArgumentCaptor.forClass(Object.class);
        verify(sender).send(eq("video.transcribed"), eq("8"), transcribed.capture());
        assertThat(((VideoTranscribedMessage) transcribed.getValue()).videoId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("[가격 갱신 이벤트는 종목코드를 키로 발행되고 runId/peerGroup/스코어 산출일이 그대로 실린다]")
    void marketPublisher_mapsTopicKeyAndPayload() {
        // given
        MarketEventPublisher publisher = new MarketEventPublisher(sender);
        LocalDate scoreDate = LocalDate.of(2026, 9, 30);

        // when
        publisher.onPriceRefreshRequested(
            new PriceRefreshRequestedEvent("run-1", "005930", PeerGroup.DOMESTIC, scoreDate));

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender).send(eq("price.refresh.requested"), eq("005930"), payload.capture());
        PriceRefreshRequestedMessage message = (PriceRefreshRequestedMessage) payload.getValue();
        assertThat(message.runId()).isEqualTo("run-1");
        assertThat(message.peerGroup()).isEqualTo("domestic");
        assertThat(message.latestScoreDate()).isEqualTo(scoreDate);
    }

    @Test
    @DisplayName("[구독 갱신 이벤트는 subscriptionId를 키로 발행된다]")
    void subscriptionPublisher_mapsTopicAndKey() {
        // given
        SubscriptionEventPublisher publisher = new SubscriptionEventPublisher(sender);

        // when
        publisher.onRenewalDue(new SubscriptionRenewalDueEvent(42L));

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender).send(eq("subscription.renewal.due"), eq("42"), payload.capture());
        assertThat(((SubscriptionRenewalDueMessage) payload.getValue()).subscriptionId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("[텔레그램 다이제스트 이벤트는 channelId를 키로 발행된다]")
    void telegramPublisher_mapsTopicAndKey() {
        // given
        TelegramFeedEventPublisher publisher = new TelegramFeedEventPublisher(sender);
        LocalDate date = LocalDate.of(2026, 9, 30);

        // when
        publisher.onDigestGenerationRequested(new TelegramDigestGenerationRequestedEvent(3L, date));

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender).send(eq("telegram.digest.generation.requested"), eq("3"), payload.capture());
        TelegramDigestGenerationRequestedMessage message = (TelegramDigestGenerationRequestedMessage) payload.getValue();
        assertThat(message.channelId()).isEqualTo(3L);
        assertThat(message.date()).isEqualTo(date);
    }

    @Test
    @DisplayName("[결제 웹훅 이벤트는 payloadHash를 키로 동기 확인 발행된다 - 브로커 확인 실패는 호출자에게 전파돼 5xx 응답이 된다]")
    void paymentPublisher_confirmsSynchronouslyAndPropagatesFailure() {
        // given
        PaymentEventPublisher publisher = new PaymentEventPublisher(sender);
        PaymentWebhookReceivedEvent event = new PaymentWebhookReceivedEvent("hash-1", "{}");

        // when
        publisher.onWebhookReceived(event);

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendAndConfirm(eq("payment.webhook.received"), eq("hash-1"), payload.capture(),
            any(Duration.class));
        assertThat(((PaymentWebhookReceivedMessage) payload.getValue()).payloadHash()).isEqualTo("hash-1");

        // 확인 실패 시 예외가 삼켜지지 않고 올라간다
        willThrow(new KafkaPublishException("payment.webhook.received", new IllegalStateException("down")))
            .given(sender).sendAndConfirm(anyString(), anyString(), any(), any(Duration.class));
        assertThatThrownBy(() -> publisher.onWebhookReceived(event)).isInstanceOf(KafkaPublishException.class);
    }
}
