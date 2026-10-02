package com.quantlime.common.config;

import com.quantlime.price.realtime.PriceTopicSubscriptionTracker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * SimpleBroker 모드(구독 레지스트리가 이 JVM 안에만 있음)에서 시세 토픽별 구독자 수를 구독/해제/
 * 연결 종료 이벤트로 센다. SimpleBroker는 목적지별 구독 목록을 밖으로 노출하지 않아 직접 세야 한다.
 *
 * <p>외부 relay 모드에서는 구독이 다른 인스턴스에 붙어 있을 수 있어 이 JVM의 카운트만으로 발행을
 * 거르면 그쪽 구독자가 시세를 못 받는다 - 그래서 relay-enabled=true에서는 이 빈 대신
 * {@link AlwaysSubscribedPriceTopicTracker}가 활성화된다(필터링 없음).
 */
@Component
@ConditionalOnProperty(prefix = "websocket.broker", name = "relay-enabled",
    havingValue = "false", matchIfMissing = true)
public class InMemoryPriceTopicSubscriptionTracker implements PriceTopicSubscriptionTracker {

    static final String PRICE_TOPIC_PREFIX = "/topic/price.";

    // sessionId → (subscriptionId → stockCode). 구독 해제 이벤트엔 목적지가 없어 여기서 역조회한다.
    private final Map<String, Map<String, String>> subscriptionsBySession = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> subscriberCountByStockCode = new ConcurrentHashMap<>();

    public InMemoryPriceTopicSubscriptionTracker(MeterRegistry meterRegistry) {
        Gauge.builder("websocket.price.subscribed.topics", subscriberCountByStockCode, Map::size)
            .description("구독자가 1명 이상 있는 시세 토픽 수")
            .register(meterRegistry);
    }

    @Override
    public boolean hasSubscribers(String stockCode) {
        AtomicInteger count = subscriberCountByStockCode.get(stockCode);
        return count != null && count.get() > 0;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (destination == null || sessionId == null || subscriptionId == null
            || !destination.startsWith(PRICE_TOPIC_PREFIX)) {
            return;
        }
        String stockCode = destination.substring(PRICE_TOPIC_PREFIX.length());
        Map<String, String> subscriptions = subscriptionsBySession.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>());
        // 같은 구독 id가 중복 이벤트로 오면 카운트가 두 번 오르지 않게 putIfAbsent로만 센다.
        if (subscriptions.putIfAbsent(subscriptionId, stockCode) == null) {
            subscriberCountByStockCode.computeIfAbsent(stockCode, key -> new AtomicInteger()).incrementAndGet();
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, String> subscriptions = subscriptionsBySession.get(accessor.getSessionId());
        if (subscriptions == null || accessor.getSubscriptionId() == null) {
            return;
        }
        String stockCode = subscriptions.remove(accessor.getSubscriptionId());
        if (stockCode != null) {
            decrement(stockCode);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Map<String, String> subscriptions = subscriptionsBySession.remove(event.getSessionId());
        if (subscriptions != null) {
            subscriptions.values().forEach(this::decrement);
        }
    }

    private void decrement(String stockCode) {
        // 0이 되면 맵에서 지워 구독이 끊긴 종목 코드가 영구히 쌓이지 않게 한다.
        subscriberCountByStockCode.computeIfPresent(stockCode, (key, count) -> count.decrementAndGet() <= 0 ? null : count);
    }
}
