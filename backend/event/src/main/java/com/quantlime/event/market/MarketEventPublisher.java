package com.quantlime.event.market;

import com.quantlime.market.event.PriceRefreshRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * core(MarketDataRefreshService)가 발행한 순수 도메인 이벤트를 Kafka로
 * 중계한다 - {@code event.videofeed.VideoFeedEventPublisher}와 동일한 원칙.
 * {@code fallbackExecution = true}가 필요한 이유도 동일 - refreshAll()은
 * 트랜잭션 안에서 도는 메서드가 아니라(수 분~수십 분 걸리는 배치 오케스트레이션),
 * 활성 트랜잭션이 없을 때 기본값(false)이면 이 리스너 자체가 조용히
 * 스킵돼 전종목 발행이 통째로 유실된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPriceRefreshRequested(PriceRefreshRequestedEvent event) {
        kafkaTemplate.send(MarketTopics.PRICE_REFRESH_REQUESTED, event.stockCode(),
            PriceRefreshRequestedMessage.of(event));
    }
}
