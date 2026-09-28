package com.quantlime.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * backend가 2대 이상으로 스케일아웃되는 환경(로컬 docker-compose.scaleout.yml,
 * 향후 운영 이중화)에서 {@code websocket.broker.relay-enabled=true}로 켜는
 * 외부 브로커 relay. 모든 인스턴스가 같은 RabbitMQ(STOMP 플러그인, TCP
 * 기본 61613)에 TCP 연결을 맺어 발행/구독을 전역으로 공유하므로,
 * {@link SimpleBrokerConfig}(인스턴스 로컬)와 달리 어느 인스턴스가
 * 브로드캐스트하든 어느 인스턴스에 붙은 구독자든 받는다
 * (docs/00-sre/SRE.md §5-1).
 *
 * <p>단, 이 relay만으로는 인스턴스마다 독립적으로 도는 시세 릴레이
 * 스케줄러의 중복 발행(문제 B)이 해결되지 않는다 - 그건
 * {@code PriceRelayLeaderGate}(별도 커밋)가 리더 인스턴스 하나만 발행하게
 * 막는 몫이다. 이 클래스는 "발행이 전역으로 공유되게" 하는 것까지만
 * 책임진다.
 *
 * <p>런타임에 TCP 연결을 맺으려면 {@code reactor-netty}+{@code netty-all}이
 * 클래스패스에 있어야 한다(Spring 공식 문서, api/build.gradle 참고) -
 * relay-enabled=false일 때는 이 클래스 자체가 로드되지 않아 안 쓰인다.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "websocket.broker", name = "relay-enabled", havingValue = "true")
@EnableConfigurationProperties(RabbitStompProperties.class)
public class RabbitStompRelayConfig implements WebSocketMessageBrokerConfigurer {

    private final RabbitStompProperties properties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        StompBrokerRelayRegistration relay = registry.enableStompBrokerRelay("/topic")
            .setRelayHost(properties.getHost())
            .setRelayPort(properties.getPort())
            .setClientLogin(properties.getClientLogin())
            .setClientPasscode(properties.getClientPasscode())
            .setSystemLogin(properties.getSystemLogin())
            .setSystemPasscode(properties.getSystemPasscode());
        // 발행 목적지 prefix는 현재 클라이언트→서버 메시지가 없어(순수
        // 서버→클라이언트 브로드캐스트) SimpleBroker와 동일하게 유지 -
        // relay 도입이 클라이언트 프로토콜을 바꾸지 않는다.
        registry.setApplicationDestinationPrefixes("/app");
        relay.setSystemHeartbeatSendInterval(10_000);
        relay.setSystemHeartbeatReceiveInterval(10_000);
    }
}
