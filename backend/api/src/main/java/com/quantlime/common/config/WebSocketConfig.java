package com.quantlime.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

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

    static final int SEND_TIME_LIMIT_MS = 10_000;
    static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;

    // 느린 클라이언트 하나가 전송 버퍼를 무한정 키워 힙을 잡아먹지 않게 상한을 둔다(docs/00-sre/SRE.md
    // §5-2 "백프레셔", 2026-10-01). 한 메시지(시세 스냅샷)가 수백 바이트라 256KB면 수백 건이 밀린 상태이고,
    // 10초 안에 한 메시지도 못 보내는 연결은 사실상 죽은 연결이다 - 둘 중 하나라도 넘으면 Spring이 세션을
    // 닫고(SESSION_NOT_RELIABLE) 클라이언트가 재연결한다(stompClient 자동 재연결).
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendTimeLimit(SEND_TIME_LIMIT_MS)
            .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stocks")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
