package com.quantlime.event.market;

import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import com.quantlime.market.service.PriceRefreshBatchGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final KafkaEventSender eventSender;
    private final PriceRefreshBatchGate priceRefreshBatchGate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPriceRefreshRequested(PriceRefreshRequestedEvent event) {
        // 발행조차 못 한 종목은 컨슈머가 완료를 통지할 일이 없어, 예전엔 한 건만 실패해도 refreshAll()의 fan-in이
        // 상한(100분)을 다 기다렸다(그동안 정규화·사분면 알림·락 해제가 밀림). DLT 핸들러와 같이 "최종 종료"로 세고,
        // 이 종목은 다음 정기 배치가 다시 갱신한다(2026-10-31, docs/00-sre/SRE.md §7-1).
        eventSender.send(MarketTopics.PRICE_REFRESH_REQUESTED, event.stockCode(),
            PriceRefreshRequestedMessage.of(event),
            () -> priceRefreshBatchGate.completeOne(event.runId(), event.peerGroup(), event.stockCode()));
    }
}
