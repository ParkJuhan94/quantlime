package com.quantlime.common.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link RabbitStompRelayConfig}가 쓰는 RabbitMQ STOMP 플러그인 접속 정보.
 * {@code websocket.broker.relay-enabled=true}일 때만 실제로 쓰인다
 * (TossApiProperties와 동일한 "@ConfigurationProperties + Getter/
 * RequiredArgsConstructor" 패턴).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "rabbitmq-stomp")
public class RabbitStompProperties {

    private final String host;
    private final int port;
    private final String clientLogin;
    private final String clientPasscode;
    private final String systemLogin;
    private final String systemPasscode;
}
