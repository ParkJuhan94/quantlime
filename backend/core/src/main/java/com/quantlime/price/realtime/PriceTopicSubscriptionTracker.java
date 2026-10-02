package com.quantlime.price.realtime;

/**
 * 종목 시세 STOMP 토픽({@code /topic/price.{stockCode}})에 지금 구독자가 있는지 알려준다.
 * 시세 릴레이 스케줄러가 구독자가 0명인 종목까지 메시지를 만들어 보내지 않도록
 * 거르는 데 쓴다(2026-10-01, docs/00-sre/SRE.md §5-2 "구독자 인지 브로드캐스트").
 *
 * <p>구현은 api 모듈이 브로커 종류별로 고른다 - SimpleBroker(인스턴스 로컬 구독)는 실제 구독
 * 이벤트를 세는 구현, 외부 relay(구독이 다른 인스턴스에 붙어 있을 수 있음)는 항상 true를
 * 돌려주는 구현(필터링 없음).
 */
public interface PriceTopicSubscriptionTracker {

    boolean hasSubscribers(String stockCode);
}
