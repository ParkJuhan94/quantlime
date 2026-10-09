package com.quantlime.market.event;

/**
 * 전종목 가격·스코어 갱신 배치(국내·해외 횡단면 정규화까지)가 끝났음을 알리는
 * 순수 도메인 이벤트. 이 시점부터 스코어가 확정이라 스코어 기반 후속 작업(사분면
 * 변화 알림 등)이 구독한다 - market이 후속 작업의 구현(notification)을 직접
 * 알지 않게 하려는 분리다.
 */
public record ScoreBatchCompletedEvent() {
}
