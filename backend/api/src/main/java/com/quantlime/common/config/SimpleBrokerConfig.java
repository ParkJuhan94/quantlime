package com.quantlime.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 기본 브로커 - JVM 힙 안에서만 구독 레지스트리를 갖는 in-memory
 * SimpleBroker. 단일 인스턴스에서는 문제없지만, backend를 2대 이상으로
 * 스케일아웃하면 인스턴스 A가 브로드캐스트한 메시지를 인스턴스 B에 붙은
 * 구독자는 받지 못한다(docs/00-sre/SRE.md §5-1) - 그래서
 * {@code websocket.broker.relay-enabled=true}(로컬 scale-out 검증,
 * 향후 운영 이중화)에서는 대신 {@link RabbitStompRelayConfig}가 활성화된다.
 * 로컬 단일 인스턴스 개발 환경의 기본값은 그대로 이 클래스(추가 인프라
 * 없이 바로 뜸)라 {@code matchIfMissing = true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "websocket.broker", name = "relay-enabled",
    havingValue = "false", matchIfMissing = true)
public class SimpleBrokerConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
