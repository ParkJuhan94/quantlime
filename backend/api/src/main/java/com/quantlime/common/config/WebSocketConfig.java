package com.quantlime.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 시세 브로드캐스트용 STOMP 엔드포인트. 토스증권 API가 아직 WebSocket을
 * 지원하지 않아(CLAUDE.md §4), 서버가 REST로 폴링한 시세를 이 브로커를 통해
 * 클라이언트에 전달한다(DomesticWatchlistPriceRelayScheduler 참고).
 *
 * <p>브로커 자체(SimpleBroker vs RabbitMQ STOMP relay)는 이 클래스가 아니라
 * {@link SimpleBrokerConfig}/{@link RabbitStompRelayConfig}가
 * {@code websocket.broker.relay-enabled} 플래그로 양자택일해 구성한다 -
 * {@code @EnableWebSocketMessageBroker}가 컨텍스트의 모든
 * {@link WebSocketMessageBrokerConfigurer} 빈을 모아 각 메서드를 호출해주는
 * 걸 이용해, 엔드포인트 등록(항상 동일)과 브로커 선택(환경별로 다름)을
 * 클래스 단위로 분리했다 - {@code DataSourceConfig}가
 * {@code @ConditionalOnProperty}로 read/write 라우팅 DataSource 전체를
 * 켜고 끄는 것과 같은 패턴(2026-09-25, feat/realtime-fanout-scaleout).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stocks")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
