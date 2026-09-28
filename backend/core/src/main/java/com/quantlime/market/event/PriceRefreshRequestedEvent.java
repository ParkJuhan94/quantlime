package com.quantlime.market.event;

import com.quantlime.score.domain.PeerGroup;
import java.time.LocalDate;

/**
 * 전종목 가격·스코어 fan-out(2026-09-24)이 종목별로 발행하는 순수 도메인
 * 이벤트 - core는 Kafka를 모른다(videofeed와 동일한 원칙, {@code
 * event.videofeed.VideoFeedEventPublisher} 참고). {@code latestScoreDate}는
 * 배치 시작 시점에 이미 조회해 둔 스냅샷 값을 그대로 실어 보낸다 - 컨슈머가
 * 종목마다 다시 조회하면 2026-09 성능 감사에서 없앤 9,000회 개별 쿼리가
 * 재발한다.
 */
public record PriceRefreshRequestedEvent(
    String runId, String stockCode, PeerGroup peerGroup, LocalDate latestScoreDate) {
}
