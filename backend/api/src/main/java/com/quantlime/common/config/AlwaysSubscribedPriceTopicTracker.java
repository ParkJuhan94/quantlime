package com.quantlime.common.config;

import com.quantlime.price.realtime.PriceTopicSubscriptionTracker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 relay 모드({@code websocket.broker.relay-enabled=true})용 - 구독이 다른 인스턴스에 붙어 있을
 * 수 있어 이 JVM만으로는 구독자 유무를 알 수 없으므로 항상 true(필터링 없음)를 돌려준다.
 * 전역 구독 레지스트리(Redis 등)로 필터링하는 건 후속 과제다({@link InMemoryPriceTopicSubscriptionTracker} 참고).
 */
@Component
@ConditionalOnProperty(prefix = "websocket.broker", name = "relay-enabled", havingValue = "true")
public class AlwaysSubscribedPriceTopicTracker implements PriceTopicSubscriptionTracker {

    @Override
    public boolean hasSubscribers(String stockCode) {
        return true;
    }
}
