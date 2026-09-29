package com.quantlime.event.telegramfeed;

import com.quantlime.telegramfeed.event.TelegramDigestGenerationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * core(TelegramDigestGenerationFacade.publishAll)가 발행한 순수 도메인
 * 이벤트를 Kafka로 중계한다 - videofeed/market/subscription/payment와
 * 동일한 원칙. {@code fallbackExecution = true}가 필요한 이유도 동일 -
 * 스케줄러의 발행 루프가 트랜잭션 밖에서 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramFeedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDigestGenerationRequested(TelegramDigestGenerationRequestedEvent event) {
        String key = String.valueOf(event.channelId());
        kafkaTemplate.send(TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED, key,
            TelegramDigestGenerationRequestedMessage.of(event.channelId(), event.date()));
    }
}
