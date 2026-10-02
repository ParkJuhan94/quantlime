package com.quantlime.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Tag("unit")
class InMemoryPriceTopicSubscriptionTrackerTest {

    private InMemoryPriceTopicSubscriptionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryPriceTopicSubscriptionTracker(new SimpleMeterRegistry());
    }

    private Message<byte[]> frame(StompCommand command, String sessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (subscriptionId != null) {
            accessor.setSubscriptionId(subscriptionId);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void subscribe(String sessionId, String subscriptionId, String destination) {
        tracker.onSubscribe(new SessionSubscribeEvent(this, frame(StompCommand.SUBSCRIBE, sessionId, subscriptionId, destination)));
    }

    @Test
    @DisplayName("[구독하면 해당 종목에 구독자가 있고, 구독하지 않은 종목은 없다]")
    void subscribe_marksStockAsSubscribed() {
        subscribe("s1", "sub-0", "/topic/price.005930");

        assertThat(tracker.hasSubscribers("005930")).isTrue();
        assertThat(tracker.hasSubscribers("000660")).isFalse();
    }

    @Test
    @DisplayName("[시세 토픽이 아닌 목적지는 무시한다]")
    void subscribe_nonPriceTopic_ignored() {
        subscribe("s1", "sub-0", "/topic/other.005930");

        assertThat(tracker.hasSubscribers("005930")).isFalse();
    }

    @Test
    @DisplayName("[두 세션이 구독한 뒤 한 세션만 해제해도 다른 세션이 남아 있으면 구독자가 있다]")
    void unsubscribe_oneOfTwo_stillSubscribed() {
        subscribe("s1", "sub-0", "/topic/price.005930");
        subscribe("s2", "sub-0", "/topic/price.005930");

        tracker.onUnsubscribe(new SessionUnsubscribeEvent(this, frame(StompCommand.UNSUBSCRIBE, "s1", "sub-0", null)));

        assertThat(tracker.hasSubscribers("005930")).isTrue();
    }

    @Test
    @DisplayName("[마지막 구독이 해제되면 구독자가 없다]")
    void unsubscribe_last_noSubscribers() {
        subscribe("s1", "sub-0", "/topic/price.005930");

        tracker.onUnsubscribe(new SessionUnsubscribeEvent(this, frame(StompCommand.UNSUBSCRIBE, "s1", "sub-0", null)));

        assertThat(tracker.hasSubscribers("005930")).isFalse();
    }

    @Test
    @DisplayName("[연결이 끊기면 그 세션의 모든 구독이 정리된다]")
    void disconnect_removesAllSessionSubscriptions() {
        subscribe("s1", "sub-0", "/topic/price.005930");
        subscribe("s1", "sub-1", "/topic/price.000660");

        Message<byte[]> disconnect = frame(StompCommand.DISCONNECT, "s1", null, null);
        tracker.onDisconnect(new SessionDisconnectEvent(this, disconnect, "s1", CloseStatus.NORMAL));

        assertThat(tracker.hasSubscribers("005930")).isFalse();
        assertThat(tracker.hasSubscribers("000660")).isFalse();
    }

    @Test
    @DisplayName("[같은 구독 id의 중복 이벤트는 한 번만 센다]")
    void subscribe_duplicateEvent_countedOnce() {
        subscribe("s1", "sub-0", "/topic/price.005930");
        subscribe("s1", "sub-0", "/topic/price.005930");

        tracker.onUnsubscribe(new SessionUnsubscribeEvent(this, frame(StompCommand.UNSUBSCRIBE, "s1", "sub-0", null)));

        assertThat(tracker.hasSubscribers("005930")).isFalse();
    }
}
