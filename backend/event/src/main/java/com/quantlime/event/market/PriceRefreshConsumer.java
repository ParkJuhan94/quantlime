package com.quantlime.event.market;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.market.service.PriceRefreshBatchGate;
import com.quantlime.score.domain.PeerGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code price.refresh.requested} 토픽을 소비해 종목 하나의 가격+스코어
 * 갱신을 처리한다(2026-09-24, 카프카 다도메인 확장 Phase 1). videofeed와
 * 동일한 패턴(non-blocking retry, attempts=4, 30s→90s→270s 백오프) -
 * quant-engine 호출의 read timeout(60초)이 있어 blocking retry는 다른
 * 종목 처리를 막아버린다.
 *
 * <p><b>동시성을 1로 고정한 이유</b>: 기존 순차 루프의 150ms 종목 간
 * 딜레이(sleepBetweenStocks)가 Toss 초당 토큰버킷 페이싱 역할을 겸했다.
 * 이 딜레이 로직을 그대로 재사용하는 대신(과설계 방지 - RateLimiter 신규
 * 도입은 보류) 리스너 동시성을 1로 둬 병렬화 이전과 동등한 처리율을
 * 유지한다. 실측 후 필요하면 동시성을 올리되, 그때는 Toss 레이트리밋을
 * 넘기지 않도록 별도 RateLimiter 도입을 재검토해야 한다.
 *
 * <p><b>DLT 핸들러가 반드시 카운터를 감소시키는 이유</b>: {@link
 * PriceRefreshBatchGate}는 발행된 종목 수만큼 감소돼야 0에 도달한다 - 이
 * 종목 하나가 재시도 소진 후 DLT로 가는데도 카운터를 안 줄이면, {@code
 * MarketDataRefreshService.refreshAll()}의 fan-in 대기가 이 배치에서
 * 영원히(타임아웃까지) 정규화를 못 하게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRefreshConsumer {

    private final MarketDataRefreshService marketDataRefreshService;
    private final PriceRefreshBatchGate priceRefreshBatchGate;
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 30_000, multiplier = 3.0, maxDelay = 270_000))
    @KafkaListener(topics = MarketTopics.PRICE_REFRESH_REQUESTED, groupId = "price-refresh-collector",
        concurrency = "1")
    public void onPriceRefreshRequested(PriceRefreshRequestedMessage message) {
        PeerGroup peerGroup = marketDataRefreshService.refreshSingleStockFromFanOut(
            message.stockCode(), message.latestScoreDate());
        priceRefreshBatchGate.completeOne(message.runId(), peerGroup);
    }

    // 절대 예외를 던지면 안 된다(2026-09-30, SubscriptionRenewalConsumer에서
    // 실제로 겪은 무한 재발행 루프 사고 - TranscriptRequestConsumer.onDlt 주석
    // 참고). 특히 이 핸들러는 completeOne(Redis DECR)이 Redis 장애 시 던질 수
    // 있는데, 그 경우에도 무한루프에 빠지면 안 되므로 try/catch가 더욱 중요하다 -
    // 다만 카운터 감소 자체가 실패하면 배치 fan-in이 못 끝나는 문제는 여전히
    // 남는다(별도 개선 과제, 일단 무한루프만 우선 차단).
    @DltHandler
    public void onDlt(PriceRefreshRequestedMessage message) {
        try {
            priceRefreshBatchGate.completeOne(message.runId(), PeerGroup.of(message.peerGroup()));
            log.error("가격 갱신 최종 실패(재시도 소진, DLT 이관) - 다음 정기 배치(16:00/20:10)에서 자동 재시도됨: "
                    + "stockCode={}, runId={}",
                message.stockCode(), message.runId());
            dltNotifier.notify("market-price-refresh", MarketTopics.PRICE_REFRESH_REQUESTED,
                "stockCode=" + message.stockCode() + ", runId=" + message.runId()
                    + " - 가격/스코어 갱신 최종 실패. 이 종목만 이번 배치에서 갱신되지 않았고, "
                    + "다음 정기 배치(16:00/20:10)에서 자동 재시도됩니다.");
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): stockCode={}, runId={}",
                message.stockCode(), message.runId(), e);
        }
    }
}
